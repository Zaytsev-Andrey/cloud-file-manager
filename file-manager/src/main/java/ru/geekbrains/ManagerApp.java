package ru.geekbrains;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Sets up and runs main window Network Manager Application
 */
public class ManagerApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("/manager.fxml"));
        primaryStage.setTitle("Cloud File Manager");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/logo.png")));
        primaryStage.setScene(new Scene(root, 720, 480));
        primaryStage.setMaximized(true);
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
