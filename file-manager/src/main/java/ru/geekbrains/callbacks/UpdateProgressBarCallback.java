package ru.geekbrains.callbacks;

/**
 * Callback to update the upload or download process
 */
public interface UpdateProgressBarCallback {
    void progress(String progressID, double value);
}
