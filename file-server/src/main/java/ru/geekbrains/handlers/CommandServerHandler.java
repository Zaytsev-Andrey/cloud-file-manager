package ru.geekbrains.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.commands.NetworkCommand;
import ru.geekbrains.configs.ServerConfig;
import ru.geekbrains.entities.FileServerClient;
import ru.geekbrains.messages.FileView;
import ru.geekbrains.messages.NetworkPackage;
import ru.geekbrains.messages.PackageBody;
import ru.geekbrains.messages.PackageHeader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles command "CD", "MKDIR", "RM" and "SEARCH".
 * "CD" - change current directory
 * "MKDIR" - create new directory
 * "RM" - remove file or directory
 * "SEARCH" - file search
 */
public class CommandServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandServerHandler.class);

    private FileServerClient activeClient;

    public CommandServerHandler(FileServerClient client) {
        this.activeClient = client;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NetworkPackage pack = (NetworkPackage) msg;
        PackageHeader header = pack.getHeader();
        NetworkCommand command = header.getCommand();
        Optional<NetworkPackage> reqPack = Optional.empty();

        if (NetworkCommand.CD.equals(command)) {
             reqPack = commandCD(header);
        } else if (NetworkCommand.SEARCH.equals(command)) {
            reqPack = commandSearch(header);
        } else if (NetworkCommand.MKDIR.equals(command)) {
            reqPack = commandMkDir(header);
        } else if (NetworkCommand.RM.equals(command)) {
            reqPack = commandRM(header);
        }

        if (reqPack.isPresent()) {
            ctx.writeAndFlush(reqPack.get());
        } else {
            ctx.flush();
        }
    }

    /**
     * Handles command RM and remove file or directory.
     * @param header - header of the inbound package
     * @return Optional<NetworkPackage> package LS
     * @throws IOException if remove file error
     */
    private Optional<NetworkPackage> commandRM(PackageHeader header) throws IOException {
        String filename = header.getParam(1);
        Path filePath = Paths.get(ServerConfig.ROOT_DIRECTORY).resolve(filename);
        Path currentPath = Paths.get(ServerConfig.ROOT_DIRECTORY).resolve(activeClient.getCurrentPath());

        if (!Files.exists(filePath)) {
            LOGGER.warn("Remove file error. File {} not exist", filePath.toString());
            return null;
        }

        try {
            if (Files.isDirectory(filePath)) {
                try (Stream<Path> walk = Files.walk(filePath)){
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            } else {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            LOGGER.warn("Remove file error", e);
        }

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.LS)
                .addParam(activeClient.getCurrentPath().toString())
                .build();

        PackageBody reqBody = new PackageBody(Files.list(currentPath).map(FileView::new).toArray());

        return Optional.of(new NetworkPackage(reqHeader, reqBody));
    }

    /**
     * Handles command MkDir and create new directory.
     * @param header - header of the inbound package
     * @return Optional<NetworkPackage> package LS
     */
    private Optional<NetworkPackage> commandMkDir(PackageHeader header) {
        String directoryName = header.getParam(1);
        Path currentPath = Paths.get(ServerConfig.ROOT_DIRECTORY).resolve(activeClient.getCurrentPath());
        Path dirPath = currentPath.resolve(directoryName);


        if (Files.exists(dirPath)) {
            LOGGER.warn("Create directory error. File {} already exist", directoryName);
            return null;
        }

        try {
            Files.createDirectory(dirPath);
        } catch (IOException e) {
            LOGGER.warn("Create directory error", e);
            return null;
        }

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.LS)
                .addParam(activeClient.getCurrentPath().toString())
                .build();
        PackageBody reqBody = new PackageBody(getRemoteFileViews(currentPath));

        return Optional.of(new NetworkPackage(reqHeader, reqBody));
    }

    /**
     * Handles command Search and finds files containing a substring (header.getParam(2)) in the name.
     * @param header - header of the inbound package
     * @return Optional<NetworkPackage> package LS
     */
    private Optional<NetworkPackage> commandSearch(PackageHeader header) {
        String directory = header.getParam(1);
        String search = header.getParam(2);
        Path pathStart = Paths.get(ServerConfig.ROOT_DIRECTORY).resolve(directory);

        List<FileView> searchedFiles = new ArrayList<>();
        try (Stream<Path> findList = Files.find(pathStart, Integer.MAX_VALUE, (path, basicFileAttributes) ->
                path.getFileName().toString().toLowerCase().contains(search.toLowerCase())
                        && !path.equals(pathStart))) {
            searchedFiles = findList.map(FileView::new).collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.warn("Search error", e);
        }

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.LS)
                .addParam(directory)
                .build();
        PackageBody reqBody = new PackageBody(searchedFiles.toArray());

        return Optional.of(new NetworkPackage(reqHeader, reqBody));
    }

    /**
     * Handles command CD and changes current directory
     * @param header - header of the inbound package
     * @return Optional<NetworkPackage> package LS
     */
    private Optional<NetworkPackage> commandCD(PackageHeader header) {
        Path tempPath;
        String filePath = header.getParam(1);

        if ("..".equals(filePath)) {
            if (activeClient.getCurrentPath().equals(Paths.get(activeClient.getHomeDirectory()))) {
                tempPath = activeClient.getCurrentPath();

            } else {
                tempPath = activeClient.getCurrentPath().getParent();
            }
        } else if ("~".equals(filePath)) {
            tempPath = activeClient.getCurrentPath();
        } else {
            tempPath = Paths.get(filePath);
        }

        Path targetPath = Paths.get(ServerConfig.ROOT_DIRECTORY).resolve(tempPath);

        if (!Files.isDirectory(targetPath)) {
            return null;
        }
        activeClient.setCurrentPath(tempPath);

        PackageHeader reqHeader = new PackageHeader.HeaderBuilder(NetworkCommand.LS)
                .addParam(tempPath.toString())
                .build();
        PackageBody reqBody = new PackageBody(getRemoteFileViews(targetPath));

        return Optional.of(new NetworkPackage(reqHeader, reqBody));
    }

    /**
     * Creates objects FileView for all files (and directories) to the current remote directory.
     * @param dir - current remote directory
     * @return array of the objects FileView
     */
    private Object[] getRemoteFileViews(Path dir) {
        Object[] files = null;
        try (Stream<Path> walk = Files.list(dir)) {
            files = walk.map(FileView::new).toArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("Handle command error", cause);
    }
}
