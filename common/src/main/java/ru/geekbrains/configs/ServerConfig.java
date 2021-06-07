package ru.geekbrains.configs;

import java.io.File;

/**
 * Application settings class
 */
public class ServerConfig {
    public static final int SERVER_PORT = 25252;
    public static final String SERVER_IP_ADDRESS = "localhost";
    public static final String ROOT_DIRECTORY = "file-server" + File.separator + "share";
    public static final int BUFFER_SIZE = 64 * 1024;
}
