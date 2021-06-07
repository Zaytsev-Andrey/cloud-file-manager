package ru.geekbrains;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.callbacks.FillRemoteTableCallback;
import ru.geekbrains.callbacks.UpdateProgressBarCallback;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.configs.ServerConfig;
import ru.geekbrains.connection.AuthStatus;
import ru.geekbrains.connection.ConnectionObservable;
import ru.geekbrains.connection.ConnectionObserver;
import ru.geekbrains.connection.ConnectionStatus;
import ru.geekbrains.handlers.AuthManagerHandler;
import ru.geekbrains.handlers.CommandManagerHandler;
import ru.geekbrains.handlers.FileDownloadManagerHandler;
import ru.geekbrains.handlers.FileUploadManagerHandler;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageHeader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Establishes a connection to the server in a new thread.
 * Provides methods for upload and download file, create directory, remove file or directory, search file,
 * authentication client.
 * Notifies the user interface of a change in connection and authentication state
 */
public class ManagerService implements ConnectionObservable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagerService.class);

    // Observers list connection and authentication state
    private List<ConnectionObserver> observers = new ArrayList<>();
    // Current uploads file
    private Map<String, FileInputStream> uploadFiles = new ConcurrentHashMap<>();
    // Current download file
    private Map<String, FileOutputStream> downloadFiles = new ConcurrentHashMap<>();

    private FillRemoteTableCallback onUploadTable;
    private UpdateProgressBarCallback onUpdateProgress;

    private volatile Channel activeChannel;

    private volatile ConnectionStatus connectionStatus;
    private volatile AuthStatus authStatus;


    public ManagerService(FillRemoteTableCallback onUploadTable, UpdateProgressBarCallback onUpdateProgress) {
        this.onUploadTable = onUploadTable;
        this.onUpdateProgress = onUpdateProgress;
        connectionStatus = ConnectionStatus.DISCONNECTED;
        authStatus = AuthStatus.NOT_AUTHENTICATED;
    }

    /**
     * Establishes a connection to the server in a new thread
     * Adds into client channel pipeline:
     *      - ObjectDecoder for deserialize bytebuf into object
     *      - ObjectEncoder for serialize NetworkPackage object into bytebuf
     *      - AuthHandler for handles command "AUTH"
     */
    public void start() {
        setConnectionStatus(ConnectionStatus.CONNECTING);
        Thread t = new Thread(() -> {
            NioEventLoopGroup workerGroup = new NioEventLoopGroup();

            try {
                Bootstrap b = new Bootstrap();
                b.group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) throws Exception {
                                ChannelPipeline pipeline = channel.pipeline();
//                                pipeline.addLast("FixedLengthFrameDecoder", new FixedLengthFrameDecoder(8 * 1024));
                                pipeline.addLast("ObjectDecoder", new ObjectDecoder(
                                                ClassResolvers.weakCachingConcurrentResolver(this.getClass().getClassLoader())));
                                pipeline.addLast("ObjectEncoder", new ObjectEncoder());
                                pipeline.addLast("AuthManagerHandler",
                                        new AuthManagerHandler(s -> setAuthStatus(s), () -> {
                                                    pipeline.addAfter("AuthManagerHandler", "CommandHandler",
                                                            new CommandManagerHandler(onUploadTable));
                                                    pipeline.addAfter("AuthManagerHandler", "FileUploadHandler",
                                                            new FileUploadManagerHandler(uploadFiles, onUpdateProgress));
                                                    pipeline.addAfter("AuthManagerHandler", "FileDownloadHandler",
                                                            new FileDownloadManagerHandler(downloadFiles, onUpdateProgress));
                                                }));

                            }
                        });

                ChannelFuture f = b.connect(ServerConfig.SERVER_IP_ADDRESS, ServerConfig.SERVER_PORT).sync();
                LOGGER.info("Service Manager is running");
                setActiveChannel(f.channel());

                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                LOGGER.error("Server connection is interrupted", e);
            } finally {
                LOGGER.info("Service Manager stopped");
                resetActiveChannel();
                workerGroup.shutdownGracefully();
            }
        });
        t.start();
    }

    /**
     * Closes connection to the server
     */
    public void stop() {
        if (ConnectionStatus.CONNECTED.equals(connectionStatus)) {
            activeChannel.close();
            setAuthStatus(AuthStatus.NOT_AUTHENTICATED);
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }
    }

    /**
     * Adds observers of current connection and authentication state
     * @param observer - new observer
     */
    @Override
    public void registerObserver(ConnectionObserver observer) {
        observers.add(observer);
        notifyObserver(observer);
    }

    /**
     * Removes observers of current connection and authentication state
     * @param observer - removed observer
     */
    @Override
    public void removeObserver(ConnectionObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies the observe of a change in connection or authentication state
     * @param observer - notifiable observer
     */
    @Override
    public void notifyObserver(ConnectionObserver observer) {
        observer.updateConnectionState(getConnectionStatus(), getAuthStatus());
    }

    /**
     * Notifies the observer of a change in connection or authentication state
     */
    @Override
    public void notifyAllObserver() {
        observers.forEach(o -> o.updateConnectionState(getConnectionStatus(), getAuthStatus()));
    }

    private synchronized void setActiveChannel(Channel ch) {
            if (ch != null) {
                activeChannel = ch;
                setConnectionStatus(ConnectionStatus.CONNECTED);
            } else {
                resetActiveChannel();
            }
    }

    private synchronized void resetActiveChannel() {
        activeChannel = null;
        setAuthStatus(AuthStatus.NOT_AUTHENTICATED);
        setConnectionStatus(ConnectionStatus.DISCONNECTED);
    }

    public synchronized ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public synchronized AuthStatus getAuthStatus() {
        return authStatus;
    }

    private synchronized void setConnectionStatus(ConnectionStatus status) {
        connectionStatus = status;
        notifyAllObserver();
    }

    private synchronized void setAuthStatus(AuthStatus status) {
        authStatus = status;
        notifyAllObserver();
    }

    public boolean isConnected() {
        if (ConnectionStatus.CONNECTED.equals(connectionStatus)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isAuthenticated() {
        if (AuthStatus.AUTHENTICATED.equals(authStatus)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Sends NetworkPackage to the server
     * @param pack - NetworkPackage
     */
    private synchronized void sendPackage(NetworkPackage pack) {
        if (isConnected()) {
            activeChannel.writeAndFlush(pack);
        } else {
            throw new RuntimeException("Client disconnected");
        }
    }

    /**
     * Sends an authentication request to the server (command AUTH)
     * @param login - client login
     * @param password - client password
     */
    public synchronized void login(String login, String password) {
        PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.AUTH)
                .addParam(login)
                .addParam(password)
                .build();
        NetworkPackage pack = new NetworkPackage(header, null);

        sendPackage(pack);
    }

    /**
     * Sends a change request to the current directory to the server (command CD)
     * @param path - new current directory
     */
    public void cd(String path) {
        if (!isAuthenticated()) {
            throw new RuntimeException("Client not authenticated");
        }
        PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.CD)
                .addParam(path)
                .build();
        NetworkPackage pack = new NetworkPackage(header, null);

        sendPackage(pack);
    }

    /**
     * Creates FileInputStream and sends a request to upload a file to the server
     * @param sourcePath - source file
     * @param targetPath - target file
     */
    public void upload(String sourcePath, String targetPath) {
        if (uploadFiles.containsKey(targetPath)) {
            throw new RuntimeException("File already uploading");

        }

        try {
            FileInputStream fis = new FileInputStream(sourcePath);
            uploadFiles.put(targetPath, fis);

            PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.UPLOAD_START)
                    .addParam(targetPath)               // target file and upload ID
                    .build();

            NetworkPackage pack = new NetworkPackage(header, null);
            sendPackage(pack);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found");
        }
    }

    /**
     * Creates FileOutputStream and sends a request to download a file to the server
     * @param sourcePath - source file
     * @param targetPath - target file
     * @throws FileNotFoundException if file not found
     */
    public void download(String sourcePath, String targetPath) throws FileNotFoundException {
        if (downloadFiles.containsKey(targetPath)) {
            throw new RuntimeException("File already uploading");
        }

        FileOutputStream fos = new FileOutputStream(targetPath);
        downloadFiles.put(targetPath, fos);

        PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.DOWNLOAD_START)
                .addParam(targetPath)               // download ID
                .addParam(sourcePath)               // source file
                .build();

        NetworkPackage pack = new NetworkPackage(header, null);
        sendPackage(pack);
    }


    /**
     * Sends a file search request to the server
     * @param path - search directory
     * @param filename - search substring
     */
    public void search(String path, String filename) {
        PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.SEARCH)
                .addParam(path)                     // Start directory
                .addParam(filename)                 // search file
                .build();

        NetworkPackage pack = new NetworkPackage(header, null);
        sendPackage(pack);
    }

    /**
     * Sends a request to create a directory to the server.
     * @param directoryName - name of new directory
     */
    public void createDirectory(String directoryName) {
        PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.MKDIR)
                .addParam(directoryName)
                .build();
        NetworkPackage pack = new NetworkPackage(header, null);

        sendPackage(pack);
    }

    /**
     * Sends a request to delete a file or directory to the server.
     * @param filename - name of remove file or directory
     */
    public void removeFile(String filename) {
        PackageHeader header = new PackageHeader.HeaderBuilder(NetworkCommand.RM)
                .addParam(filename)
                .build();
        NetworkPackage pack = new NetworkPackage(header, null);

        sendPackage(pack);
    }
}
