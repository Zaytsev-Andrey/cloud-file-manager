package ru.geekbrains;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import ru.geekbrains.callbacks.SearchFileCallback;
import ru.geekbrains.callbacks.UpdateTableCallback;
import ru.geekbrains.messages.FileView;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Manages the elements of the local and remote directory
 */
public class TableController implements Initializable {
    @FXML
    private TextField searchField;
    @FXML
    private Button buttonUp;
    @FXML
    private ComboBox diskBox;
    @FXML
    private TableView<FileView> fileTable;
    @FXML
    private TextField currentPath;

    private DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    private UpdateTableCallback updateCall;
    private SearchFileCallback onSearchFile;
    private TableColumn<FileView, String> filePathCol;

    /**
     * Initializes columns local and remote table
     * @param location
     * @param resources
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initFileTypeColumn();
        initFilenameColumn();
        initFileSizeColumn();
        initUpdatingTimeColumn();
        initCreationTimeColumn();
        initFilePathColumn();
        initButtonUp();

        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                fileTableItemAction();
            }
        });
    }

    /**
     * Initializes file type column
     */
    private void initFileTypeColumn() {
        TableColumn<FileView, String> fileTypeCol = new TableColumn<>();
        fileTypeCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getFileType()));
        fileTypeCol.setPrefWidth(30);
        fileTypeCol.setCellFactory(col ->
                new TableCell<FileView, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setText(null);
                            setStyle("");
                            setGraphic(null);
                        } else if (item.equals("D")) {
                            setText("");
                            setGraphic(getIcon("/icons/folder.png"));
                        } else if (item.equals("F")) {
                            setGraphic(getIcon("/icons/file.png"));
                        }
                    }
                }
        );

        fileTable.getColumns().add(fileTypeCol);
        fileTypeCol.setSortType(TableColumn.SortType.ASCENDING);
        fileTable.getSortOrder().add(fileTypeCol);
    }

    /**
     * Initializes filename column
     */
    private void initFilenameColumn() {
        TableColumn<FileView, String> filenameCol = new TableColumn<>("Name");
        filenameCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getFilename()));
        filenameCol.setPrefWidth(200);

        fileTable.getColumns().add(filenameCol);
    }

    /**
     * Initializes file size column
     */
    private void initFileSizeColumn() {
        TableColumn<FileView, Long> fileSizeCol = new TableColumn<>("Size");
        fileSizeCol.setCellValueFactory(p -> new SimpleObjectProperty(p.getValue().getSize()));
        fileSizeCol.setCellFactory(col ->
                new TableCell<FileView, Long>() {
                    @Override
                    protected void updateItem(Long item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setText(null);
                            setStyle("");
                        } else if (item != -1) {
                            String value = String.format("%,d byte", item);
                            setText(value);
                            setAlignment(Pos.CENTER_RIGHT);
                        } else {
                            setText("");
                        }
                    }
                }
        );
        fileSizeCol.setPrefWidth(100);

        fileTable.getColumns().add(fileSizeCol);
    }

    /**
     * Initializes file updating time column
     */
    private void initUpdatingTimeColumn() {
        TableColumn<FileView, String> updatingTimeCol = new TableColumn<>("Last modified time");
        updatingTimeCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getLastModifiedTime().format(df)));
        updatingTimeCol.setPrefWidth(120);

        fileTable.getColumns().add(updatingTimeCol);
    }

    /**
     * Initializes file creation time column
     */
    private void initCreationTimeColumn() {
        TableColumn<FileView, String> creationTimeCol = new TableColumn<>("Creation time");
        creationTimeCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getCreatingTime().format(df)));
        creationTimeCol.setPrefWidth(120);

        fileTable.getColumns().add(creationTimeCol);
    }

    /**
     * Initializes file path column
     */
    private void initFilePathColumn() {
        filePathCol = new TableColumn<>("Path");
        filePathCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getDirectory()));
        filePathCol.setPrefWidth(300);
        filePathCol.setVisible(false);

        fileTable.getColumns().add(filePathCol);
    }

    /**
     * Initializes button Up
     */
    private void initButtonUp() {
        buttonUp.setGraphic(getIcon("/icons/back.png"));
    }

    /**
     * Initialize disk box
     * @param rootPaths
     */
    public void initDiskBox(List<Path> rootPaths) {
        Platform.runLater(() -> {
            diskBox.getItems().clear();
            diskBox.getItems().addAll(rootPaths);
            diskBox.getSelectionModel().select(0);
        });

    }

    /**
     * Create new ImageView
     * @param iconPath - icon path
     * @return ImageView of icon
     */
    private ImageView getIcon(String iconPath) {
        ImageView imageView = new ImageView();
        imageView.setFitHeight(16);
        imageView.setFitWidth(16);
        imageView.setImage(new Image(getClass().getResourceAsStream(iconPath)));
        return imageView;
    }

    /**
     * Handles event selecting of table element
     */
    public void fileTableItemAction() {
        if (fileTable.getSelectionModel().getSelectedItem() != null) {
            String fileName = fileTable.getSelectionModel().getSelectedItem().getFilename();
            String filePath = fileTable.getSelectionModel().getSelectedItem().getDirectory();
            updateCall.update(Paths.get(filePath).resolve(fileName).toString());
            setFilePathVisible(false);
        }
    }

    /**
     * Updates table content
     * @param fileViews - new content
     * @param path - current path
     */
    public void updateTable(List<FileView> fileViews, String path) {
        Platform.runLater(() -> {
            fileTable.getItems().clear();
            fileTable.getItems().addAll(fileViews);
            currentPath.setText(path);
            fileTable.sort();
        });
    }

    /**
     * Clears table content
     */
    public void clearTable() {
        Platform.runLater(() -> {
            fileTable.getItems().clear();
            currentPath.clear();
        });
    }

    public TableView<FileView> getFileTable() {
        return fileTable;
    }

    /**
     * Sets table update callback
     * @param updateCall - callback
     */
    public void setUpdateCall(UpdateTableCallback updateCall) {
        this.updateCall = updateCall;
    }

    /**
     * Sets file search callback
     * @param onSearchFile - callback
     */
    public void setSearchCall(SearchFileCallback onSearchFile) {
        this.onSearchFile = onSearchFile;
    }

    /**
     * Handles the button Up event.
     */
    public void btnUpAction() {
        updateCall.update("..");
        setFilePathVisible(false);
    }

    public FileView getSelectedFile() {
        if (!fileTable.isFocused()) {
            return null;
        }
        return fileTable.getSelectionModel().getSelectedItem();
    }

    /**
     * Handles the search event.
     */
    public void btnSearchAction() {
        onSearchFile.search(searchField.getText());
        fileTable.requestFocus();
    }

    /**
     * Changes the visibility state of the file path column
     * @param state new visibility state
     */
    public void setFilePathVisible(boolean state) {
        Platform.runLater(() -> {
            filePathCol.setVisible(state);
        });

    }

    public boolean isFocused() {
        return currentPath.isFocused() || fileTable.isFocused() || searchField.isFocused();
    }

    /**
     * Handles selected disk box event
     * @param actionEvent - current event
     */
    public void diskBoxAction(ActionEvent actionEvent) {
        ComboBox<Path> element = (ComboBox<Path>) actionEvent.getSource();
        Path path = element.getSelectionModel().getSelectedItem();
        updateCall.update(path.toString());
    }
}
