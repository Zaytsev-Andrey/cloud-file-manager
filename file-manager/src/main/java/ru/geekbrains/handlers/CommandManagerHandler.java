package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.callbacks.FillRemoteTableCallback;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.messages.FileView;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageBody;
import ru.geekbrains.messages.PackageHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles command LS and update the contents of the remote table
 */
public class CommandManagerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManagerHandler.class);
    FillRemoteTableCallback onUploadTable;

    public CommandManagerHandler(FillRemoteTableCallback onUploadTable) {
        this.onUploadTable = onUploadTable;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        PackageBody body = pack.getBody();

        if (NetworkCommand.LS.equals(header.getCommand())) {
            List<FileView> listFiles = new ArrayList<>();
            for (int i = 0; i < body.getObjectBody().length; i++) {
                listFiles.add((FileView) body.getObjectBody()[i]);
            }
            onUploadTable.fill(listFiles, header.getParam(1));
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Manager handler error", cause);
    }
}
