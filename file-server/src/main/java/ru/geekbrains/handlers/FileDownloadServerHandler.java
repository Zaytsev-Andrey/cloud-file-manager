package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.configs.ServerConfig;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageBody;
import ru.geekbrains.messages.PackageHeader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles command "DOWNLOAD_START", "DOWNLOAD_READY", "DOWNLOAD_DONE" and "DOWNLOAD_FAIL".
 * "DOWNLOAD_START" - create new FileInputStream
 * "DOWNLOAD_READY" - read bytes into the buffer and send package "DOWNLOAD" or "DOWNLOAD_FINISH" if file read complete
 * "DOWNLOAD_DONE" - close FileInputStream
 * "DOWNLOAD_FAIL" - close FileInputStream and logging error message
 */
public class FileDownloadServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadServerHandler.class);

    private Map<String, FileInputStream> downloadFiles = new HashMap<>();
    private String lastDownload;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        NetworkCommand command = header.getCommand();

        if (NetworkCommand.DOWNLOAD_START.equals(command)) {
            String downloadID = header.getParam(1);
            lastDownload = downloadID;
            Path targetPath = Paths.get(ServerConfig.ROOT_DIRECTORY, header.getParam(2));

            FileInputStream fis = new FileInputStream(targetPath.toFile());
            downloadFiles.put(downloadID, fis);

            downloadPartFile(ctx, downloadID);
        } else if (NetworkCommand.DOWNLOAD_READY.equals(command)) {
            String downloadID = header.getParam(1);
            lastDownload = downloadID;
            downloadPartFile(ctx, downloadID);
        } else if (NetworkCommand.DOWNLOAD_DONE.equals(command)) {
            String downloadID = header.getParam(1);
            lastDownload = downloadID;
            FileInputStream fis = downloadFiles.get(downloadID);
            fis.close();
            downloadFiles.remove(downloadID);
        } else if (NetworkCommand.DOWNLOAD_FAIL.equals(command)) {
            String downloadID = header.getParam(1);
            String errMessage = header.getParam(2);
            FileInputStream fis = downloadFiles.get(downloadID);
            fis.close();
            downloadFiles.remove(downloadID);
            LOGGER.warn(errMessage);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Reads bytes into the buffer and sends package "DOWNLOAD" or "DOWNLOAD_FINISH" if file read complete
     * @param ctx - channel handler context
     * @param downloadID - session ID
     * @throws IOException if file reading error
     */
    private void downloadPartFile(ChannelHandlerContext ctx, String downloadID) throws IOException {
        FileInputStream fis = downloadFiles.get(downloadID);
        byte[] buffer = new byte[ServerConfig.BUFFER_SIZE];
        int read = fis.read(buffer);
        PackageHeader reqHeader;
        if (read != -1) {
            reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.DOWNLOAD)
                    .addParam(downloadID)
                    .addParam(String.valueOf(read))         // read bytes
                    .build();
        } else {
            reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.DOWNLOAD_FINISH)
                    .addParam(downloadID)
                    .build();
        }
        NetworkPackage reqPack = new NetworkPackage(reqHeader, new PackageBody(buffer));
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
                .addParam("Error reading remote file")
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }
}
