package ru.geekbrains;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.geekbrains.connection.AuthStatus;
import ru.geekbrains.connection.ConnectionObserver;
import ru.geekbrains.connection.ConnectionStatus;

/**
 * The class AuthController manages the elements of the authentication window.
 * Handle events onAction for buttons: Login and Cancel.
 */
public class AuthController implements ConnectionObserver {
    @FXML
    private Label authStatusLabel;
    @FXML
    private TextField loginField;
    @FXML
    private TextField passwordField;

    private Stage authStage;
    private ManagerService managerService;
    private ConnectionStatus connectionStatus;
    private AuthStatus authStatus;

    /**
     * Handles events of update states of the connection and auth
     * @param connection new state variable
     * @param auth new state variable
     */
    @Override
    public void updateConnectionState(ConnectionStatus connection, AuthStatus auth) {
        if (AuthStatus.AUTHENTICATION_FAIL.equals(auth)) {
            Platform.runLater(() -> authStatusLabel.setText(auth.getMessage()));
        }

        connectionStatus = connection;
        authStatus = auth;
    }

    public void setAuthStage(Stage authStage) {
        this.authStage = authStage;
    }

    public void setManagerService(ManagerService managerService) {
        this.managerService = managerService;
    }

    /**
     * Handles button Login event
     */
    public void btnLoginAction() {
        managerService.login(loginField.getText(), passwordField.getText());
        clear();
    }

    /**
     * Handles button Cancel event
     */
    public void btnCancelAction() {
        managerService.stop();
        authStage.hide();
        clear();
    }

    /**
     * Clears fields login and password
     */
    private void clear() {
        loginField.clear();
        passwordField.clear();
        loginField.requestFocus();
    }
}
