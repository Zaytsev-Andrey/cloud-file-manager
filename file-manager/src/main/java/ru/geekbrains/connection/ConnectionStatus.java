package ru.geekbrains.connection;

/**
 *  Connection states
 */
public enum ConnectionStatus {
    DISCONNECTED ("Connection not established"),
    CONNECTING ("Connecting..."),
    CONNECTED ("Connected established");

    String message;

    ConnectionStatus(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
