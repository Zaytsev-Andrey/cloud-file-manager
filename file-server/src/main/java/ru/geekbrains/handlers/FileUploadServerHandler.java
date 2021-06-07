package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.configs.ServerConfig;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageHeader;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles command "UPLOAD_START", "UPLOAD", "UPLOAD_FINISH" and "UPLOAD_FAIL".
 * "UPLOAD_START" - create FileOutputStream
 * "UPLOAD" - write buffer into the FileOutputStream and send command "UPLOAD_READY"
 * "UPLOAD_FINISH" - close FileOutputStream and send command "UPLOAD_DONE"
 * "UPLOAD_FAIL" - close FileOutputStream and logging error message
 */
public class FileUploadServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadServerHandler.class);

    private Map<String, FileOutputStream> uploadFiles = new HashMap<>();
    private String lastUpload;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        NetworkCommand command = header.getCommand();

        if (NetworkCommand.UPLOAD_START.equals(command)) {
            String uploadID = header.getParam(1);
            lastUpload = uploadID;
            Path targetPath = Paths.get(".", ServerConfig.ROOT_DIRECTORY, header.getParam(1));

            FileOutputStream fos = new FileOutputStream(targetPath.toFile());
            uploadFiles.put(uploadID, fos);

            writeReadyUpload(ctx, uploadID);
        } else if (NetworkCommand.UPLOAD.equals(command)) {
            String uploadID = header.getParam(1);
            lastUpload = uploadID;
            FileOutputStream fos = uploadFiles.get(uploadID);
            byte[] buffer = pack.getBody().getByteBody();
            int read = Integer.parseInt(header.getParam(2));
            fos.write(buffer, 0, read);
            fos.flush();
            writeReadyUpload(ctx, uploadID);
        } else if (NetworkCommand.UPLOAD_FINISH.equals(command)) {
            String uploadID = header.getParam(1);
            lastUpload = uploadID;
            FileOutputStream fos = uploadFiles.get(uploadID);
            fos.close();
            uploadFiles.remove(uploadID);
            PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_DONE)
                    .addParam(uploadID)
                    .build();
            NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
            ctx.writeAndFlush(reqPack);
        } else if (NetworkCommand.UPLOAD_FAIL.equals(command)) {
            String uploadID = header.getParam(1);
            String errMessage = header.getParam(2);
            FileOutputStream fos = uploadFiles.get(uploadID);
            fos.close();
            uploadFiles.remove(uploadID);
            LOGGER.warn(errMessage);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * Sends command "UPLOAD_READY"
     * @param ctx - channel handler context
     * @param uploadID - session ID
     */
    private void writeReadyUpload(ChannelHandlerContext ctx, String uploadID) {
        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_READY)
                .addParam(uploadID)
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Upload error", cause);

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_FAIL)
                .addParam(lastUpload)
                .addParam("Error writing remote file")
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }
}
