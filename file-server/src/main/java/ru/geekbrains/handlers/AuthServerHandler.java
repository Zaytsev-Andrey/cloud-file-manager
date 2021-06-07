package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.db.DBConnection;
import ru.geekbrains.entities.FileServerClient;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageHeader;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Handles command "AUTH". If authentication passed then send package "AUTH_OK" to the client and add into channel pipeline
 * CommandHandler, FileUploadHandler, FileDownloadHandler and remove AuthServerHandler.
 */
public class AuthServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServerHandler.class);

    private DBConnection connection;
    private FileServerClient activeClient;

    public AuthServerHandler(DBConnection connection) {
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        NetworkPackage reqPack;

        if (NetworkCommand.AUTH.equals(header.getCommand())) {
            reqPack = commandAuth(header);
            ctx.writeAndFlush(reqPack);

            if (NetworkCommand.AUTH_OK.equals(reqPack.getHeader().getCommand())) {
                ChannelPipeline pipeline = ctx.channel().pipeline();
                pipeline.addAfter("AuthHandler", "CommandHandler", new CommandServerHandler(activeClient));
                pipeline.addAfter("AuthHandler", "FileUploadHandler", new FileUploadServerHandler());
                pipeline.addAfter("AuthHandler", "FileDownloadHandler", new FileDownloadServerHandler());
                pipeline.remove(this);
            }
        } else {
            ctx.flush();
        }
    }

    /**
     * Extracts login and password from header and calls method login from connection
     * @param header - header of the package
     * @return package "AUTH_OK" if authentication passed or package "AUTH_FAIL" if authentication failed
     * @throws SQLException
     */
    private NetworkPackage commandAuth(PackageHeader header) throws SQLException {
        String login = header.getParam(1);
        String password = header.getParam(2);

        Optional<FileServerClient> client = connection.login(login, password);

        PackageHeader reqHeader;
        if (client.isPresent()) {
            activeClient = client.get();

            reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.AUTH_OK)
                    .addParam(activeClient.getNickname())
                    .build();
        } else {
            reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.AUTH_FAIL)
                    .addParam("Incorrect login or password")
                    .build();
        }

        return new NetworkPackage(reqHeader, null);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Authentication failed", cause);
    }
}
