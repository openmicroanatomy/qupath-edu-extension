package qupath.edu.gui.dialogs;

import com.google.common.collect.MoreCollectors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.edu.models.ExternalSlide;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static qupath.edu.api.EduAPI.Result;

public class ExternalSlideManager {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();

    private final static Logger logger = LoggerFactory.getLogger(ExternalSlideManager.class);

    private static Dialog dialog;

    private BorderPane pane;
    private TableView<ExternalSlide> table;

    public static void showExternalSlideManager() {
        ExternalSlideManager manager = new ExternalSlideManager();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog = Dialogs.builder()
                .title("External Slide Manager")
                .content(manager.getPane())
                .buttons(ButtonType.CLOSE)
                .width(Math.min(800, screenSize.getWidth() / 2))
                .build();

        dialog.showAndWait();
    }

    public BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        /* Table */

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Text("No slides, none match search criteria or no permissions."));
        table.setEditable(true);

        TableColumn<ExternalSlide, Boolean> selectedColumn = new TableColumn<>();
        selectedColumn.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
        selectedColumn.setEditable(true);
        selectedColumn.setReorderable(false);
        selectedColumn.setResizable(false);
        selectedColumn.setMinWidth(28);
        selectedColumn.setMaxWidth(28);

        TableColumn<ExternalSlide, String> slideNameColumn = new TableColumn<>("Name");
        slideNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        slideNameColumn.setReorderable(false);

        TableColumn<ExternalSlide, String> ownerColumn = new TableColumn<>("Owner");
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("ownerReadable"));
        ownerColumn.setReorderable(false);

        TableColumn<ExternalSlide, String> uuidColumn = new TableColumn<>("UUID");
        uuidColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        uuidColumn.setReorderable(false);

        table.getColumns().addAll(selectedColumn, slideNameColumn, ownerColumn, uuidColumn);

        /* Filter / Search */

        TextField filterTextField = new TextField();
        filterTextField.setPromptText("Search by slide name, organization or ID");

        FilteredList<ExternalSlide> filteredData = new FilteredList<>(FXCollections.observableArrayList(EduAPI.getAllSlides()), data -> true);

        filterTextField.textProperty().addListener(((observable, oldValue, newValue) -> {
            filteredData.setPredicate(data -> {
                if (newValue == null || newValue.isEmpty() || data.isSelected()) {
                    return true;
                }

                String lowerCaseSearch = newValue.toLowerCase();
                return data.toString().replaceAll("[-+.^:,']", "").toLowerCase().contains(lowerCaseSearch);
            });
        }));

        SortedList<ExternalSlide> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());

        table.setItems(sortedData);

        /* Buttons */

        BooleanBinding hasWriteAccess = Bindings.createBooleanBinding(() -> {
            ExternalSlide selectedItem = table.getSelectionModel().getSelectedItem();

            if (selectedItem == null) {
                return false;
            } else {
                return EduAPI.isOwner(selectedItem.getOwner().getId());
            }
        }, table.getSelectionModel().selectedItemProperty());

        BooleanBinding slideSelected = table.getSelectionModel().selectedItemProperty().isNull();
        BooleanBinding canManageSlides = new SimpleBooleanProperty(EduAPI.hasRole(Roles.MANAGE_SLIDES)).not();

        Button btnAdd = new Button("Add selected");
        btnAdd.setTooltip(new Tooltip("Add selected slides to current project"));
        btnAdd.disableProperty().bind(qupath.projectProperty().isNull());
        btnAdd.setOnAction(e -> addImages());

        Button btnOpen = new Button("Open slide");
        btnOpen.setOnAction(e -> openSlide(table.getSelectionModel().getSelectedItem()));
        btnOpen.disableProperty().bind(slideSelected);

        Button btnRename = new Button("Rename");
        btnRename.setOnAction(e -> renameSlide());
        btnRename.disableProperty().bind(slideSelected.or(hasWriteAccess.not()).or(canManageSlides));

        Button btnDelete = new Button("Delete");
        btnDelete.setOnAction(e -> deleteSlide());
        btnDelete.disableProperty().bind(slideSelected.or(hasWriteAccess.not()).or(canManageSlides));

        MenuItem miCopyID = new MenuItem("Copy ID");
        miCopyID.setOnAction(e -> copySlideID());

        MenuItem miCopyURL = new MenuItem("Copy URL");
        miCopyURL.setOnAction(e -> copySlideURL());

        MenuItem miViewProperties = new MenuItem("View properties");
        miViewProperties.setOnAction(e -> viewProperties());
        miViewProperties.disableProperty().bind(slideSelected);

        MenuButton menuMore = new MenuButton("More ...");
        menuMore.getItems().addAll(miCopyID, miCopyURL, miViewProperties);
        menuMore.disableProperty().bind(slideSelected);

        Button btnUpload = new Button("Upload new slide");
        btnUpload.setOnAction(e -> uploadSlide());
        btnUpload.disableProperty().bind(canManageSlides);

        GridPane paneButtons = PaneTools.createColumnGridControls(btnAdd, btnOpen, btnRename, btnDelete, menuMore, btnUpload);
        paneButtons.setHgap(5);

        /* Pane */

        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        pane = new BorderPane();
        pane.setPrefWidth(800);
        pane.setPrefHeight(400);
        pane.setTop(filterTextField);
        pane.setCenter(table);
        pane.setBottom(paneButtons);
        pane.setPadding(new Insets(10));
    }

    private void addImages() {
        List<String> urls = new ArrayList<>();

        table.getItems().stream().filter(ExternalSlide::isSelected).forEach(slide -> {
            urls.add(EduAPI.getHost() + "/" + EduAPI.e(slide.getId()) + "#" + EduAPI.e(slide.getName()));
        });

        // Only add slides which have its' checkbox selected.
        // If no slides have a checked checkbox, we'll add the slide which is just selected, it should never be null.
        if (urls.isEmpty()) {
            ExternalSlide selected = table.getSelectionModel().getSelectedItem();
            urls.add(EduAPI.getHost() + "/" + EduAPI.e(selected.getId()) + "#" + EduAPI.e(selected.getName()));
        }

        dialog.close();

        Platform.runLater(() -> ProjectCommands.promptToImportImages(qupath, urls.toArray(new String[0])));
    }

    private void copySlideURL() {
        Dialogs.showInputDialog("Slide URL", "", EduAPI.getHost() + "/" + table.getSelectionModel().getSelectedItem().getId());
    }

    private void copySlideID() {
        Dialogs.showInputDialog("Slide ID", "", table.getSelectionModel().getSelectedItem().getId());
    }

    public synchronized void refreshDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshDialog);
            return;
        }

        initializePane();
        dialog.getDialogPane().setContent(pane);
    }

    public static void openSlide(ExternalSlide slide) {
        if (qupath.getProject() != null) {
            ButtonType closeProject = new ButtonType("Close project and continue", ButtonBar.ButtonData.FINISH);
            ButtonType addToProject = new ButtonType("Add slide to project",       ButtonBar.ButtonData.OK_DONE);


            Optional<ButtonType> confirm = Dialogs.builder()
                .title("Proceed with adding slide to project")
                .contentText(
                    "Opening an slide with a project open will try to add that slide to the current project." +
                    "\n\n" +
                    "Do you wish to close your project and continue or add this slide to your current project?"
                ).buttons(closeProject, ButtonType.CANCEL, addToProject)
                .build()
                .showAndWait();

            if (confirm.isEmpty() || confirm.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                return;
            }

            if (confirm.get().getButtonData() == ButtonBar.ButtonData.FINISH) {
                qupath.setProject(null);
            }
        }

        if (dialog != null) {
            dialog.close();
        }

        Platform.runLater(() -> {
            qupath.openImage(EduAPI.getHost() + "/" + slide.getId(), true, true);
// TODO:           qupath.getTabbedPanel().getSelectionModel().select(1);

            // Loading a slide will prompt to set ImageType. This marks ImageData as changed prompts and prompts pointlessly to save changes.
            qupath.getImageData().setChanged(false);
        });
    }

    private void viewProperties() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();

        TableView<Map.Entry<String, String>> propertiesTable = new TableView<>();
        propertiesTable.setPrefWidth(800);
        propertiesTable.setPrefHeight(500);
        propertiesTable.setPadding(new Insets(0));
        propertiesTable.setEditable(true);

        TableColumn<Map.Entry<String, String>, String> keyColumn = new TableColumn<>("Key");
        keyColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().getKey()));
        keyColumn.setSortType(TableColumn.SortType.ASCENDING);

        TableColumn<Map.Entry<String, String>, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().getValue()));
        valueColumn.setEditable(true);

        propertiesTable.getColumns().addAll(keyColumn, valueColumn);
        propertiesTable.getItems().addAll(slide.getParameters().entrySet());
        propertiesTable.getSortOrder().add(keyColumn);

        Dialogs.builder()
                .buttons(ButtonType.CLOSE)
                .content(propertiesTable)
                .resizable()
                .build()
                .showAndWait();
    }

    private static final int CHUNK_BUFFER_SIZE = 1024 * 1024;

    private void uploadSlide() {
        File file = Dialogs.promptForFile("Select slide", null, null);

        if (file == null) {
            return;
        }

        try {
            ImageServerBuilder<?> openSlideBuilder = ImageServerProvider.getInstalledImageServerBuilders().stream().filter(
                imageServerBuilder -> imageServerBuilder.getName().equalsIgnoreCase("OpenSlide builder")
            ).collect(MoreCollectors.onlyElement());

            ImageServerBuilder.UriImageSupport<?> support = openSlideBuilder.checkImageSupport(file.toURI());

            if (support.getSupportLevel() == 0) {
                Dialogs.showWarningNotification("Error uploading slide", "Given file is not supported by OpenSlide.");
                return;
            }

            Task<Void> task = new UploadSlideTask(file);
            ProgressDialog progress = new ProgressDialog(task);
            progress.setTitle("Uploading slide");
            progress.getDialogPane().setGraphic(null);
            progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
                if (Dialogs.showYesNoDialog("Cancel upload", "Are you sure you want to stop uploading this slide?")) {
                    task.cancel();
                    progress.setHeaderText("Cancelling...");
                    progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
                }
                e.consume();
            });

            QuPathGUI.getInstance().submitShortTask(task);
            progress.showAndWait();

            Dialogs.showMessageDialog(
                "Successfully uploaded slide",
                "The slide was successfully uploaded but is pending processing. Processing can take up to 30 minutes." +
                "\n\n" +
                "You can view your slide in a few minutes but it is missing higher magnifications until the processing is complete. "
            );

            refreshDialog();
        } catch (IOException e) {
            logger.error("Error while reading file", e);
        }
    }

    private void deleteSlide() {
        boolean confirm = Dialogs.showConfirmDialog(
            "Delete slide",
            "Are you sure you wish to delete this slide? This is irreversible."
        );

        if (!confirm) {
            return;
        }

        ExternalSlide slide = table.getSelectionModel().getSelectedItem();
        Result result = EduAPI.deleteSlide(slide.getId());

        if (result == Result.OK) {
            Dialogs.showInfoNotification("Success", "Successfully deleted slide.");
            refreshDialog();
        } else {
            Dialogs.showErrorNotification("Error", "Error while deleting slide. See log for possibly additional information.");
        }
    }

    private void renameSlide() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();
        String name = Dialogs.showInputDialog("New slide name", "", slide.getName());

        if (name == null) {
            return;
        }

        Result result = EduAPI.editSlide(slide.getId(), name);

        if (result == Result.OK) {
            Dialogs.showInfoNotification("Success", "Successfully renamed slide.");
            refreshDialog();
        } else {
            Dialogs.showErrorNotification("Error", "Error while renaming slide. See log for possibly additional information.");
        }
    }

    private class UploadSlideTask extends Task<Void> {

        private File file;
        private String filename;
        private long fileSize;

        private int chunkIndex = 0;
        private long chunks;

        public UploadSlideTask(File file) {
            this.file = file;
            this.filename = file.getName();
            this.fileSize = file.length();
            this.chunks = Math.floorDiv(fileSize, CHUNK_BUFFER_SIZE);
        }

        @Override
        protected Void call() throws Exception {
            try (FileInputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[CHUNK_BUFFER_SIZE];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    EduAPI.uploadSlideChunk(
                        filename,
                        fileSize,
                        Arrays.copyOf(buffer, read),
                        CHUNK_BUFFER_SIZE,
                        chunkIndex
                    );

                    updateMessage(String.format("Uploading chunk %s out of %s", chunkIndex, chunks));
                    updateProgress(chunkIndex, chunks);
                    chunkIndex++;
                }
            } catch (IOException e) {
                logger.error("Error while uploading slide", e);
            }

            return null;
        }
    }
}
