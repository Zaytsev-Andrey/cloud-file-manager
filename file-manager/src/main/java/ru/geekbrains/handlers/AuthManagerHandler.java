package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.callbacks.LoadHandlersCallback;
import ru.geekbrains.callbacks.SetAuthStatusCallback;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.connection.AuthStatus;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageHeader;

/**
 * Handles command "AUTH_OK" and "AUTH_FAIL".
 * "AUTH_OK" - update authentication state then add into channel pipeline
 * CommandHandler, FileUploadHandler, FileDownloadHandler and remove AuthManagerHandler.
 */
public class AuthManagerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManagerHandler.class);
    SetAuthStatusCallback setStatus;
    LoadHandlersCallback onLoadHandler;

    public AuthManagerHandler(SetAuthStatusCallback setStatus, LoadHandlersCallback onLoadHandler) {
        this.setStatus = setStatus;
        this.onLoadHandler = onLoadHandler;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();

        if (NetworkCommand.AUTH_OK.equals(header.getCommand())) {
            setStatus.set(AuthStatus.AUTHENTICATED);

            // Rebuild pipeline
            ChannelPipeline pipeline = ctx.pipeline();
            onLoadHandler.load();
            pipeline.remove(this);
        } else if (NetworkCommand.AUTH_FAIL.equals(header.getCommand())) {
            AuthStatus authStatus = AuthStatus.AUTHENTICATION_FAIL;
            authStatus.setMessage(header.getParam(1));
            setStatus.set(authStatus);
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
