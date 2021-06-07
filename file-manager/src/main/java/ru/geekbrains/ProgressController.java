package ru.geekbrains;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import ru.geekbrains.configs.ServerConfig;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Manages the display of processes upload or download file
 */
public class ProgressController implements Initializable {
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;

    private double step;

    /**
     * Sets start value progress bar
     * @param location
     * @param resources
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        progressBar.setProgress(0);
    }

    /**
     * Sets header operation
     * @param label - header operation
     */
    public void setProgressLabel(String label) {
        progressLabel.setText(label);
    }

    /**
     * Calculates step
     * @param fileSize - file size
     */
    public void calcStep(long fileSize) {
        step = 100.0 / (1.0 * fileSize / ServerConfig.BUFFER_SIZE) / 100;
    }

    /**
     * Makes a step
     */
    public void progress() {
        progressBar.setProgress(progressBar.getProgress() + step);
    }

    /**
     * Close window
     */
    public void close() {
        Platform.runLater( () -> ((Stage) progressBar.getScene().getWindow()).close());
    }
}
