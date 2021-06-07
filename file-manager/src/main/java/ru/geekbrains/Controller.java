package ru.geekbrains;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.connection.AuthStatus;
import ru.geekbrains.connection.ConnectionObserver;
import ru.geekbrains.connection.ConnectionStatus;
import ru.geekbrains.messages.FileView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The class Controller manages the elements of the main window Network Manager Application.
 * Handle events onAction for buttons: Upload or Download, Create directory, Remove File or Directory.
 */
public class Controller implements Initializable, ConnectionObserver {
    @FXML
    private Button btnUploadOrDownload;
    @FXML
    private MenuItem connectMenuItem;
    @FXML
    private MenuItem disconnectMenuItem;
    @FXML
    private VBox leftTable;
    @FXML
    private VBox rightTable;

    private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    private Map<String, ProgressController> progressControllers = new ConcurrentHashMap<>();

    private Stage authStage;
    private AuthController authController;

    private ConnectionStatus connectionStatus;
    private AuthStatus authStatus;

    private List<FileView> remoteTableList;
    private Path localCurrentDir;
    private String remoteCurrentPath;
    private TableController lTable;
    private TableController rTable;

    private ManagerService managerService;

    /**
     * Handles event onAction menu Exit and close application
     */
    public void btnExitAction() {
        exit();
    }

    /**
     * Initializes left (local) and right (remove) table of the FileView.
     * Starts connection to the server.
     * Sets callbacks for update content of tables and search results
     * Initializes handles events onKeyReleased
     * @param location
     * @param resources
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> btnUploadOrDownload.getScene().getWindow().setOnCloseRequest(event -> exit()));

        // init of links to file view tables
        lTable = (TableController) leftTable.getProperties().get("ctrl");
        rTable = (TableController) rightTable.getProperties().get("ctrl");

        // Connecting server
        initConnectCloudStorage();
        connect();

        // Init start values of directories
        localCurrentDir = Paths.get(".").toAbsolutePath().normalize();
        remoteCurrentPath = "~";

        // Init callback
        lTable.setUpdateCall(this::updateLocalCurrentPath);
        rTable.setUpdateCall(this::updateRightTable);
        lTable.setSearchCall(this::searchLocalFile);
        rTable.setSearchCall(this::searchRemoteFile);

        // Init table event
        initLeftTableEvent();
        initRightTableEvent();

        initLeftDiskBox();
    }

    /**
     * Handles events of update states of the connection and auth
     * @param connection new state variable
     * @param auth new state variable
     */
    @Override
    public void updateConnectionState(ConnectionStatus connection, AuthStatus auth) {
        if (ConnectionStatus.CONNECTED.equals(connection) && !connection.equals(connectionStatus)) {
            Platform.runLater(() -> authStage.show());
            connectMenuItem.setDisable(true);
            disconnectMenuItem.setDisable(false);
        }

        if (ConnectionStatus.DISCONNECTED.equals(connection) && !connection.equals(connectionStatus)) {
            rTable.clearTable();
            connectMenuItem.setDisable(false);
            disconnectMenuItem.setDisable(true);
            btnUploadOrDownload.setDisable(true);
        }

        if (AuthStatus.AUTHENTICATED.equals(auth) && !auth.equals(authStatus)) {
            Platform.runLater(() -> authStage.hide());
            initRightDiskBox();
            managerService.cd("~");
            btnUploadOrDownload.setDisable(false);
        }

        connectionStatus = connection;
        authStatus = auth;
    }

    /**
     * Sets handlers for key of the left (local) table: Enter, Backspace, F5, F7 and F8
     */
    private void initLeftTableEvent() {
        lTable.getFileTable().setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                lTable.fileTableItemAction();
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                lTable.btnUpAction();
            } else if (event.getCode() == KeyCode.F5) {
                btnUploadOrDownloadAction();
            } else if (event.getCode() == KeyCode.F7) {
                btnCreateDirectoryAction();
            } else if (event.getCode() == KeyCode.F8) {
                btnRemoveAction();
            }
        });
    }

    /**
     * Sets handlers for key of the right (remote) table: Enter, Backspace, F5, F7 and F8
     */
    private void initRightTableEvent() {
        rTable.getFileTable().setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                rTable.fileTableItemAction();
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                rTable.btnUpAction();
            } else if (event.getCode() == KeyCode.F5) {
                btnUploadOrDownloadAction();
            } else if (event.getCode() == KeyCode.F7) {
                btnCreateDirectoryAction();
            } else if (event.getCode() == KeyCode.F8) {
                btnRemoveAction();
            }
        });
    }

    /**
     * Creates and initializes new ManagerService
     */
    private void initConnectCloudStorage() {
        initAuthStage();
        managerService = new ManagerService(
                (l, p) -> {
                    rTable.updateTable(l, p);
                    remoteTableList = l;
                    remoteCurrentPath = p;
                },
                (id, d) -> {
                    if (d == 1) {
                        progressControllers.get(id).progress();
                    } else  {
                        if (d == -1) {
                            showWarnAlert("Operation failed");
                        }
                        progressControllers.get(id).close();
                        progressControllers.remove(id);
                        managerService.cd("~");
                        updateLeftTable();
                    }
                });
        authController.setAuthStage(authStage);
        authController.setManagerService(managerService);
        managerService.registerObserver(this);
        managerService.registerObserver(authController);
    }

    /**
     * Creates window authentication
     */
    private void initAuthStage() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/auth.fxml"));
            Parent root = fxmlLoader.load();

            authController = fxmlLoader.getController();

            authStage = new Stage();
            authStage.initStyle(StageStyle.UNDECORATED);
            authStage.initModality(Modality.APPLICATION_MODAL);
            authStage.setResizable(false);
            authStage.setTitle("Authentication");
            authStage.setScene(new Scene(root,250, 180));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates list of local disks
     */
    private void initLeftDiskBox() {
        List<Path> rootPaths = new ArrayList<>();
        for (Path path : FileSystems.getDefault().getRootDirectories()) {
            rootPaths.add(path);
        }
        lTable.initDiskBox(rootPaths);
    }

    /**
     * Sets remote disk as "\"
     */
    private void initRightDiskBox() {
        List<Path> rootPaths = new ArrayList<>();
            rootPaths.add(Paths.get("\\"));
        rTable.initDiskBox(rootPaths);
    }

    /**
     * Creates object Path from string "filename", saves to the localCurrentDir variable and
     * update left (local) table
     * @param filename string current directory
     */
    private void updateLocalCurrentPath(String filename) {
        Path tempPath;
        if ("..".equals(filename)) {
            tempPath = localCurrentDir.getParent();
        } else {
            tempPath = Paths.get(filename);
        }
        if (tempPath == null || !Files.isDirectory(tempPath)) {
            return;
        }

        localCurrentDir = tempPath;
        updateLeftTable();
    }

    /**
     * Sends list of objects FileView to the left table controller
     */
    private void updateLeftTable() {
        try {
            lTable.setFilePathVisible(false);
            lTable.updateTable(getLocalFileViews(), localCurrentDir.normalize().toString());
        } catch (RuntimeException e) {
            LOGGER.warn("View of file wasn't create", e.getCause());
        } catch (IOException e) {
            LOGGER.warn("List of fileView wasn't create", e);
        }
    }

    /**
     * Sends command CD to the server, for to go to the directory "filePath"
     * @param filePath parameter of the command CD
     */
    private void updateRightTable(String filePath) {
        if ("\\".equals(filePath)) {
            filePath = "~";
        }
        managerService.cd(filePath);
    }

    /**
     * Creates objects FileView for all files (and directories) to the current local directory.
     * @return List of objects FileView.
     * @throws IOException if file access deny.
     */
    private List<FileView> getLocalFileViews() throws IOException {
        try (Stream<Path> files = Files.list(localCurrentDir)){
            return files.map(FileView::new).collect(Collectors.toList());
        }
    }

    /**
     * Starts the connection to the server.
     */
    private void connect() {
        managerService.start();
    }

    /**
     * Finishes the connection to the server.
     */
    private void disconnect() {
        managerService.stop();
    }

    /**
     * Handles menu Connect event and starts the connection to the server.
     */
    public void btnConnectMenuItemAction() {
        connect();
    }

    /**
     * Handles menu Disconnect event and finishes the connection to the server .
     */
    public void btnDisconnectMenuItemAction() {
        disconnect();
    }

    /**
     * Handles button Upload or Download event
     */
    public void btnUploadOrDownloadAction() {
        if (lTable.getSelectedFile() == null && rTable.getSelectedFile() == null) {
            showWarnAlert("File not selected");
            return;
        }

        // If selected left panel then upload operation
        // else download operation
        FileView selectedFile;
        if ((selectedFile = lTable.getSelectedFile()) != null) {
            upload(selectedFile.getFilename());
        } else if ((selectedFile = rTable.getSelectedFile()) != null) {
            download(selectedFile);
        }
    }

    /**
     * Download "file" from server
     * @param file - name of the download file
     */
    private void download(FileView file) {
        if (file.isDirectory()) {
            showWarnAlert("Selected folder");
            return;
        }

        String filename = file.getFilename();
        try {

            if (getLocalFileViews().stream().map(FileView::getFilename).anyMatch(filename::equals)) {
                showWarnAlert("File already exist in the local directory");
                return;
            }
        } catch (IOException e) {
            String errMessage = "Error read local directory";
            showWarnAlert(errMessage);
            LOGGER.warn(errMessage, e);
            return;
        }

        String targetPath = localCurrentDir.resolve(filename).toString();
        String sourcePath = remoteCurrentPath + File.separator + filename;

        ProgressController progress = new ProgressController();
        try {
            progress = createProgressStage("Downloading...", sourcePath, file.getSize());
            progressControllers.put(targetPath, progress);
            managerService.download(sourcePath, targetPath);
        } catch (RuntimeException | FileNotFoundException e) {
            progress.close();
            progressControllers.remove(targetPath);
            showWarnAlert(e.getMessage());
            LOGGER.warn("Download error: {}", e.getMessage(), e.getCause());
        }
    }

    /**
     * Upload "file" from server
     * @param filename - name of the upload file
     */
    private void upload(String filename) {
        Path sourcePath = localCurrentDir.resolve(filename);
        if (Files.isDirectory(sourcePath)) {
            showWarnAlert("Selected folder");
            return;
        }

        if (remoteTableList.stream().map(FileView::getFilename).anyMatch(filename::equals)) {
            showWarnAlert("File already exist in the remote directory");
            return;
        }

        String targetPath = remoteCurrentPath + File.separator + filename;

        ProgressController progress = new ProgressController();
        try {
            progress = createProgressStage("Uploading...", sourcePath.toString(), Files.size(sourcePath));
            progressControllers.put(targetPath, progress);
            managerService.upload(sourcePath.toString(), targetPath);
        } catch (RuntimeException e) {
            progress.close();
            progressControllers.remove(targetPath);
            showWarnAlert(e.getMessage());
            LOGGER.warn("Upload error: {}", e.getMessage(), e.getCause());
        } catch (IOException e) {
            LOGGER.warn("Upload error: ", e.getCause());
        }
    }

    /**
     * Create window of view upload or download process
     * @param task - ID upload or download
     * @param filename - name of the upload or download file
     * @param fileSize - size of the upload or download file
     * @return
     */
    private ProgressController createProgressStage(String task, String filename, long fileSize) {
        ProgressController progressController;

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/progress.fxml"));
            Parent root = fxmlLoader.load();

            progressController = fxmlLoader.getController();
            progressController.setProgressLabel("Upload: " + filename);
            progressController.calcStep(fileSize);

            Stage progressStage = new Stage();
            progressStage.initStyle(StageStyle.DECORATED);
            progressStage.setResizable(false);
            progressStage.setTitle(task);
            progressStage.setScene(new Scene(root,350, 150));
            progressStage.show();
        } catch (IOException e) {
            throw new RuntimeException("Error creating progress window", e);
        }

        return progressController;
    }

    /**
     * Finds file (or directory) contains substring "filename" in the name
     * @param filename
     */
    private void searchLocalFile(String filename) {
        try {
            List<FileView> searchedFiles = Files.find(localCurrentDir, Integer.MAX_VALUE, (path, basicFileAttributes) ->
                    path.getFileName().toString().toLowerCase().contains(filename.toLowerCase())
                        && !path.equals(localCurrentDir)).map(FileView::new).collect(Collectors.toList());

            lTable.setFilePathVisible(true);
            lTable.updateTable(searchedFiles, localCurrentDir.toString());
        } catch (IOException e) {
            showWarnAlert(e.getMessage());
            LOGGER.warn("Search error", e);
        }
    }

    /**
     * Calls search method to the ManagerService object
     * @param filename
     */
    private void searchRemoteFile(String filename) {
        managerService.search(remoteCurrentPath, filename);
        rTable.setFilePathVisible(true);
    }

    /**
     * Show warm message
     * @param message - warning massage string
     */
    private void showWarnAlert(String message) {
        Alert errAlert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        errAlert.showAndWait();
    }

    /**
     * Handles button Create directory event
     */
    public void btnCreateDirectoryAction() {
        String directoryName;
        TextInputDialog dialog = new TextInputDialog("New_directory");
        dialog.setTitle("New directory");
        dialog.setHeaderText("Enter directory name");
        dialog.getEditor().setPrefWidth(300);
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            directoryName = result.get();
        } else {
            return;
        }

        if (lTable.isFocused()) {
            try {
                createLocalDirectory(directoryName);
            } catch (IOException e) {
                String errMessage = "Create directory error";
                showWarnAlert(errMessage);
                LOGGER.warn(errMessage, e);
            }
        } else if (rTable.isFocused()) {
            createRemoteDirectory(directoryName);
        }
    }

    /**
     * Calls createDirectory method to the ManagerService object
     * @param directoryName - directory name
     */
    private void createRemoteDirectory(String directoryName) {
        if (remoteTableList.stream().map(FileView::getFilename).anyMatch(directoryName::equals)) {
            showWarnAlert("File already exist");
            return;
        }

        managerService.createDirectory(directoryName);
    }

    /**
     * Creates new directory in the current local directory
     * @param directoryName - directory name
     * @throws IOException if create directory error
     */
    private void createLocalDirectory(String directoryName) throws IOException {
        Path dirPath = localCurrentDir.resolve(directoryName);
        if (Files.exists(dirPath)) {
            showWarnAlert("File already exist");
            return;
        }

        Files.createDirectory(dirPath);
        updateLeftTable();
    }

    /**
     * Handles button Remove file or directory event
     */
    public void btnRemoveAction() {
        if (lTable.getSelectedFile() == null && rTable.getSelectedFile() == null) {
            showWarnAlert("File not selected");
            return;
        }

        FileView selectedFile;
        if ((selectedFile = lTable.getSelectedFile()) != null) {
            try {
                removeLocalFile(selectedFile);
                updateLeftTable();
            } catch (IOException e) {
                String errMessage = "Remove file error";
                showWarnAlert(errMessage);
                LOGGER.warn(errMessage, e);
            }
        } else if ((selectedFile = rTable.getSelectedFile()) != null) {
            String filename = selectedFile.getDirectory() + File.separator + selectedFile.getFilename();
            managerService.removeFile(filename);
        }
    }

    /**
     * Remove file or directory in the current local directory
     * @param selectedFile - name of the remove file
     * @throws IOException if remove file error
     */
    private void removeLocalFile(FileView selectedFile) throws IOException {
        Path filePath = Paths.get(selectedFile.getDirectory()).resolve(selectedFile.getFilename());
        if (Files.isDirectory(filePath)) {
            try (Stream<Path> walk = Files.walk(filePath)){
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } else {
            Files.delete(filePath);
        }
    }

    /**
     * Exit from application. Finishes the connection to the server.
     */
    private void exit() {
        disconnect();
        Platform.exit();
    }
}
