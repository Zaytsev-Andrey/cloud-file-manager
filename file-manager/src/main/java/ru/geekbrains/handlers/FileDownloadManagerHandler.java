package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.callbacks.UpdateProgressBarCallback;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageHeader;

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles command "DOWNLOAD", "DOWNLOAD_FINISH" and "DOWNLOAD_FAIL".
 * "DOWNLOAD" - write buffer into the FileOutputStream and send command "DOWNLOAD_READY"
 * "DOWNLOAD_FINISH" - close FileOutputStream and send command "DOWNLOAD_DONE"
 * "DOWNLOAD_FAIL" - close FileInputStream and logging error message
 */
public class FileDownloadManagerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileDownloadManagerHandler.class);

    private Map<String, FileOutputStream> downloadFiles = new HashMap<>();
    private UpdateProgressBarCallback onUpdateProgress;
    private String lastDownload;

    public FileDownloadManagerHandler(Map<String, FileOutputStream> downloadFiles, UpdateProgressBarCallback onUpdateProgress) {
        this.downloadFiles = downloadFiles;
        this.onUpdateProgress = onUpdateProgress;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        NetworkCommand command = header.getCommand();

        if (NetworkCommand.DOWNLOAD.equals(command)) {
            String downloadID = header.getParam(1);
            lastDownload = downloadID;
            FileOutputStream fos = downloadFiles.get(downloadID);
            byte[] buffer = pack.getBody().getByteBody();
            int read = Integer.parseInt(header.getParam(2));
            fos.write(buffer, 0, read);
            fos.flush();
            writeReadyDownload(ctx, downloadID);
            onUpdateProgress.progress(downloadID, 1);
        } else if (NetworkCommand.DOWNLOAD_FINISH.equals(command)) {
            String downloadID = header.getParam(1);
            lastDownload = downloadID;
            FileOutputStream fos = downloadFiles.get(downloadID);
            fos.close();
            downloadFiles.remove(downloadID);
            PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.DOWNLOAD_DONE)
                    .addParam(downloadID)
                    .build();
            NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
            ctx.writeAndFlush(reqPack);
            onUpdateProgress.progress(downloadID, 0);
        } else if (NetworkCommand.UPLOAD_FAIL.equals(command)) {
            String downloadID = header.getParam(1);
            String errMessage = header.getParam(2);
            FileOutputStream fos = downloadFiles.get(downloadID);
            fos.close();
            downloadFiles.remove(downloadID);
            onUpdateProgress.progress(downloadID, -1);
            LOGGER.warn(errMessage);
        } else {
            ctx.fireChannelRead(msg);
        }

    }

    /**
     * Sends command "DOWNLOAD_READY"
     * @param ctx - channel handler context
     * @param uploadID - session ID
     */
    private void writeReadyDownload(ChannelHandlerContext ctx, String uploadID) {
        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.DOWNLOAD_READY)
                .addParam(uploadID)
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Download error", cause);

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.DOWNLOAD_FAIL)
                .addParam(lastDownload)
                .addParam("Error writing remote file")
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }
}
