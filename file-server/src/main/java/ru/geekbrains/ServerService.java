package ru.geekbrains;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.configs.ServerConfig;
import ru.geekbrains.db.DBConnection;
import ru.geekbrains.db.MySQLConnection;
import ru.geekbrains.handlers.AuthServerHandler;

import java.sql.SQLException;

/**
 * Settings and runs Netty server
 * Adds into server channel pipeline:
 *      - ObjectDecoder for deserialize bytebuf into object
 *      - ObjectEncoder for serialize NetworkPackage object into bytebuf
 *      - AuthHandler for handles command "AUTH"
 */
public class ServerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerService.class);

    public ServerService() {
        EventLoopGroup authGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try (DBConnection dbConnection = new MySQLConnection()) {
            LOGGER.info("DB connected");

            ServerBootstrap b = new ServerBootstrap();
            b.group(authGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast("ObjectDecoder", new ObjectDecoder(
                                            ClassResolvers.weakCachingConcurrentResolver(this.getClass().getClassLoader())));
                            pipeline.addLast("ObjectEncoder", new ObjectEncoder());
                            pipeline.addLast("AuthHandler", new AuthServerHandler(dbConnection));
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            LOGGER.warn("Client connection interrupted", cause);
                        }
                    });

            ChannelFuture f = b.bind(ServerConfig.SERVER_PORT).sync();
            LOGGER.info("Server started");

            f.channel().closeFuture().sync();
        } catch (SQLException e) {
            LOGGER.error("Data base connection error", e);
        } catch (ClassNotFoundException e) {
            LOGGER.error("JDBC class not found", e);
        } catch (Exception e) {
            LOGGER.error("Server error", e);
        } finally {
            authGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
