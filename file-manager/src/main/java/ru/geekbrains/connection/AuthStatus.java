package ru.geekbrains.connection;

/**
 * Authentication states
 */
public enum AuthStatus {
    NOT_AUTHENTICATED ("Not authenticated"),
    AUTHENTICATED ("Authenticated"),
    AUTHENTICATION_FAIL("Authentication filed");

    String message;

    AuthStatus(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
