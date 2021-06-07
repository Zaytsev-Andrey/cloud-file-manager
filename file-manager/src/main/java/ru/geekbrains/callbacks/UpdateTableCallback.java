package ru.geekbrains.callbacks;

/**
 * Callback to update the contents of the local table
 */
@FunctionalInterface
public interface UpdateTableCallback {
    void update(String path);
}
