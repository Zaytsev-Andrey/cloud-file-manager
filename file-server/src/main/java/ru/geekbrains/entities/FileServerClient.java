package ru.geekbrains.entities;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Active client object. Include login, nickname and home directory of the client
 */
public class FileServerClient {
    private String login;
    private String nickname;
    private String homeDirectory;
    private Path currentPath;

    public FileServerClient(String login, String nickname, String homeDirectory) {
        this.login = login;
        this.nickname = nickname;
        this.homeDirectory = homeDirectory;
        this.currentPath = Paths.get(homeDirectory);
    }

    public String getNickname() {
        return nickname;
    }

    public String getHomeDirectory() {
        return homeDirectory;
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(Path path) {
        this.currentPath = path;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + login.hashCode();
        result = 31 * result + nickname.hashCode();
        result = 31 * result + homeDirectory.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof FileServerClient)) {
            return false;
        }

        FileServerClient other = (FileServerClient) obj;
        return this.login.equals(other.login) && this.nickname.equals(other.nickname)
                && this.homeDirectory.equals(other.homeDirectory);
    }
}
