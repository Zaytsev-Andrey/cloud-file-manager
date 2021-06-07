package ru.geekbrains.connection;

/**
 * Current connection and authentication state observer
 */
public interface ConnectionObserver {
    void updateConnectionState(ConnectionStatus connection, AuthStatus auth);
}
