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
import java.io.IOException;
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

    private ChannelHandlerContext context;
    private NetworkPackage currentPack;
    private Map<String, FileOutputStream> uploadFiles = new HashMap<>();
    private String uploadID;
    private String lastUploadID;

    abstract private class Uploader {
        final public void load() throws IOException {
            readInboundPackage();

            NetworkCommand answerCommand = fileWorking();

            if (answerCommand != null) {
                writeAnswerPackage(answerCommand);
            }
        }

        private void readInboundPackage() {
            uploadID = currentPack.getHeader().getParam(1);
            lastUploadID = uploadID;
        }

        abstract public NetworkCommand fileWorking() throws IOException;

        private void writeAnswerPackage(NetworkCommand command) {
            PackageHeader reqHeader = new PackageHeader.HeaderBuilder(command)
                    .addParam(uploadID)
                    .build();
            NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
            context.writeAndFlush(reqPack);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        context = ctx;
        NetworkPackage pack = (NetworkPackage) msg;
        currentPack = pack;
        PackageHeader header = currentPack.getHeader();
        NetworkCommand command = header.getCommand();

        if (NetworkCommand.UPLOAD_START.equals(command)) {
            uploadStart();
        } else if (NetworkCommand.UPLOAD.equals(command)) {
            upload();
        } else if (NetworkCommand.UPLOAD_FINISH.equals(command)) {
            uploadFinish();
        } else if (NetworkCommand.UPLOAD_FAIL.equals(command)) {
            uploadFail();
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void uploadStart() throws IOException {
        new Uploader() {
            @Override
            public NetworkCommand fileWorking() throws IOException {
                Path targetPath = Paths.get(".", ServerConfig.ROOT_DIRECTORY, currentPack.getHeader().getParam(1));
                FileOutputStream fos = new FileOutputStream(targetPath.toFile());
                uploadFiles.put(uploadID, fos);
                return NetworkCommand.UPLOAD_READY;
            }
        }.load();
    }

    private void upload() throws IOException {
        new Uploader() {
            @Override
            public NetworkCommand fileWorking() throws IOException {
                FileOutputStream fos = uploadFiles.get(uploadID);
                byte[] buffer = currentPack.getBody().getByteBody();
                int read = Integer.parseInt(currentPack.getHeader().getParam(2));
                fos.write(buffer, 0, read);
                fos.flush();
                return NetworkCommand.UPLOAD_READY;
            }
        }.load();
    }

    private void uploadFinish() throws IOException {
        new Uploader() {
            @Override
            public NetworkCommand fileWorking() throws IOException {
                FileOutputStream fos = uploadFiles.get(uploadID);
                fos.close();
                uploadFiles.remove(uploadID);
                return NetworkCommand.UPLOAD_DONE;
            }
        }.load();
    }

    private void uploadFail() throws IOException {
        new Uploader() {
            @Override
            public NetworkCommand fileWorking() throws IOException {
                String errMessage = currentPack.getHeader().getParam(2);
                FileOutputStream fos = uploadFiles.get(uploadID);
                fos.close();
                uploadFiles.remove(uploadID);
                LOGGER.warn(errMessage);
                return null;
            }
        }.load();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * Sends command "UPLOAD_READY"
     */

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Upload error", cause);

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_FAIL)
                .addParam(lastUploadID)
                .addParam("Error writing remote file")
                .build();
        NetworkPackage reqPack = new NetworkPackage(reqHeader, null);
        ctx.writeAndFlush(reqPack);
    }
}
