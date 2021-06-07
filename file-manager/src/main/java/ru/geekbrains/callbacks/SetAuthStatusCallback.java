package ru.geekbrains.callbacks;

import ru.geekbrains.connection.AuthStatus;

/**
 * Callback to update the current authentication state
 */
public interface SetAuthStatusCallback {
    void set(AuthStatus status);
}
