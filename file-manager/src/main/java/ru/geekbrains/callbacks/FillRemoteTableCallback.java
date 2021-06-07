package ru.geekbrains.callbacks;

import ru.geekbrains.messages.FileView;

import java.util.List;

/**
 * Callback to update the contents of the remote table
 */
@FunctionalInterface
public interface FillRemoteTableCallback {
    void fill(List<FileView> files, String path);
}
