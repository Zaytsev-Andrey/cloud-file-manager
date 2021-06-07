package ru.geekbrains.messages;

import ru.geekbrains.configs.ServerConfig;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Contains file view for upload to the server, download to the client and show to the client's interface
 */
public class FileView implements Serializable {
    private enum FileType {
        FILE("F"),
        DIRECTORY("D");

        private String type;

        FileType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    private FileType fileType;
    private String filename;
    private long size;
    private String directory;
    private LocalDateTime creatingTime;
    private LocalDateTime lastModifiedTime;

    public FileView(Path path) {
        try {
            this.fileType = Files.isDirectory(path) ? FileType.DIRECTORY : FileType.FILE;
            this.filename = path.getFileName().toString();
            this.size = fileType == FileType.FILE ? Files.size(path) : -1L;
            this.directory = path.getParent().toString();
            if (this.directory.startsWith(ServerConfig.ROOT_DIRECTORY)) {
                this.directory = this.directory.substring(ServerConfig.ROOT_DIRECTORY.length() + 1);
            }
            BasicFileAttributes atr = Files.readAttributes(path, BasicFileAttributes.class);
            this.creatingTime = LocalDateTime.ofInstant(atr.lastModifiedTime().toInstant(), ZoneOffset.ofHours(3));
            this.lastModifiedTime = LocalDateTime.ofInstant(atr.creationTime().toInstant(), ZoneOffset.ofHours(3));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getFileType() {
        return fileType.getType();
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public String getDirectory() {
        return directory;
    }

    public LocalDateTime getCreatingTime() {
        return creatingTime;
    }

    public LocalDateTime getLastModifiedTime() {
        return lastModifiedTime;
    }

    public boolean isDirectory() {
        return fileType == FileType.DIRECTORY;
    }
}
