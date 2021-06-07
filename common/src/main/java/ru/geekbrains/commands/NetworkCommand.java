package ru.geekbrains.commands;

/**
 * Contains commands for interaction between server and client
 */
public enum NetworkCommand {
    LS,
    MKDIR,
    CD,
    RM,
    SEARCH,
    UPLOAD,
    UPLOAD_START,
    UPLOAD_READY,
    UPLOAD_FINISH,
    UPLOAD_DONE,
    UPLOAD_FAIL,
    DOWNLOAD,
    DOWNLOAD_START,
    DOWNLOAD_READY,
    DOWNLOAD_FINISH,
    DOWNLOAD_DONE,
    DOWNLOAD_FAIL,
    AUTH,
    AUTH_OK,
    AUTH_FAIL,
}
