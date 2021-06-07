package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.callbacks.UpdateProgressBarCallback;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.configs.ServerConfig;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageBody;
import ru.geekbrains.messages.PackageHeader;

import java.io.FileInputStream;
import java.util.Map;

/**
 * Handles command "UPLOAD_READY", "UPLOAD_DONE" and "UPLOAD_FAIL".
 * "UPLOAD_READY" - read bytes into the buffer and send package "UPLOAD" or "UPLOAD_FINISH" if file read complete
 * "UPLOAD_DONE" - close FileInputStream
 * "UPLOAD_FAIL" - close FileInputStream and logging error message
 */
public class FileUploadManagerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadManagerHandler.class);

    private Map<String, FileInputStream> uploadFiles;
    private UpdateProgressBarCallback onUpdateProgress;
    private String lastUpload;


    public FileUploadManagerHandler(Map<String, FileInputStream> uploadFiles, UpdateProgressBarCallback onUpdateProgress) {
        this.uploadFiles = uploadFiles;
        this.onUpdateProgress = onUpdateProgress;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        NetworkCommand command = header.getCommand();

        if (NetworkCommand.UPLOAD_READY.equals(command)) {
            String uploadID = header.getParam(1);
            lastUpload = uploadID;
            FileInputStream fis = uploadFiles.get(uploadID);
            byte[] buffer = new byte[ServerConfig.BUFFER_SIZE];
            int read = fis.read(buffer);
            PackageHeader reqHeader;
            if (read != -1) {
                reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD)
                        .addParam(uploadID)
                        .addParam(String.valueOf(read))         // read bytes
                        .build();
            } else {
                reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_FINISH)
                        .addParam(uploadID)
                        .build();
            }
            NetworkPackage reqPack = new NetworkPackage(reqHeader, new PackageBody(buffer));
            ctx.writeAndFlush(reqPack);
            onUpdateProgress.progress(uploadID, 1);
        } else if (NetworkCommand.UPLOAD_DONE.equals(command)) {
            String uploadID = header.getParam(1);
            lastUpload = uploadID;
            FileInputStream fis = uploadFiles.get(uploadID);
            fis.close();
            uploadFiles.remove(uploadID);
            onUpdateProgress.progress(uploadID, 0);
        } else if (NetworkCommand.UPLOAD_FAIL.equals(command)) {
            String uploadID = header.getParam(1);
            String errMessage = header.getParam(2);
            FileInputStream fis = uploadFiles.get(uploadID);
            fis.close();
            uploadFiles.remove(uploadID);
            onUpdateProgress.progress(uploadID, -1);
            LOGGER.warn(errMessage);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Upload error", cause);

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_FAIL)
                .addParam(lastUpload)
                .addParam("Error reading local file")
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }
}
