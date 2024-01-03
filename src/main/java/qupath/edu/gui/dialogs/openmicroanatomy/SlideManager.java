package qupath.edu.gui.dialogs.openmicroanatomy;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
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
import qupath.edu.EduExtension;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.edu.models.ExternalSlide;
import qupath.edu.server.EduImageServer;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.images.ImageData;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.*;

import static qupath.edu.api.EduAPI.Result;

public class SlideManager {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();

    private final static Logger logger = LoggerFactory.getLogger(SlideManager.class);

    private static Dialog<ButtonType> dialog;

    private BorderPane pane;
    private TableView<ExternalSlide> table;

    public static void show() {
        SlideManager manager = new SlideManager();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog = Dialogs.builder()
                .title("Slide manager")
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Text("No slides, none match search criteria or no permissions to list slides."));
        table.setEditable(true);

        TableColumn<ExternalSlide, Boolean> selectedColumn = new TableColumn<>();
        selectedColumn.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
        selectedColumn.setEditable(true);
        selectedColumn.setSortable(false);
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

        TableColumn<ExternalSlide, Boolean> tiledColumn = new TableColumn<>("Tiled");
        tiledColumn.setCellValueFactory(row -> new ReadOnlyBooleanWrapper(row.getValue().isTiled()));
        tiledColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
        tiledColumn.setEditable(false);
        tiledColumn.setSortable(false);
        tiledColumn.setReorderable(false);
        tiledColumn.setResizable(false);
        tiledColumn.setMinWidth(50);
        tiledColumn.setMaxWidth(50);

        table.getColumns().addAll(selectedColumn, slideNameColumn, ownerColumn, tiledColumn);

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

            if (selectedItem == null) return false;

            return EduAPI.hasRole(Roles.ADMIN) ||
                    (EduAPI.hasRole(Roles.MANAGE_SLIDES) && Objects.equals(EduAPI.getUserOrganizationId(), selectedItem.getOwner().getId()));
        }, table.getSelectionModel().selectedItemProperty());

        BooleanBinding slideSelected = table.getSelectionModel().selectedItemProperty().isNotNull();
        BooleanBinding canManageSlides = new SimpleBooleanProperty(EduAPI.hasRole(Roles.MANAGE_SLIDES)).not();
        BooleanBinding isTiled = Bindings.createBooleanBinding(() -> {
            ExternalSlide selectedItem = table.getSelectionModel().getSelectedItem();

            if (selectedItem == null) return false;

            return selectedItem.isTiled();
        }, table.getSelectionModel().selectedItemProperty());

        Button btnAddRemote = new Button("Add selected");
        btnAddRemote.setTooltip(new Tooltip("Add selected slides to current lesson"));
        btnAddRemote.disableProperty().bind(
            qupath.projectProperty().isNull().or(
            qupath.readOnlyProperty().or(
            slideSelected.not()
        )));

        btnAddRemote.setOnAction(e -> addImages());

        Button btnAddLocal = new Button("Add local slide");
        btnAddLocal.setTooltip(new Tooltip("Add a slide stored locally to current lesson"));
        btnAddLocal.disableProperty().bind(qupath.projectProperty().isNull().or(qupath.readOnlyProperty()));
        btnAddLocal.setOnAction(e -> ProjectCommands.promptToImportImages(qupath));

        Button btnOpen = new Button("Open selected");
        btnOpen.setOnAction(e -> openSlide(table.getSelectionModel().getSelectedItem()));
        btnOpen.disableProperty().bind(slideSelected.not());

        MenuItem miRename = new MenuItem("Rename");
        miRename.setOnAction(e -> renameSlide());
        miRename.disableProperty().bind(slideSelected.not().or(hasWriteAccess.not()).or(canManageSlides));

        MenuItem miDelete = new MenuItem("Delete");
        miDelete.setOnAction(e -> deleteSlide());
        miDelete.disableProperty().bind(slideSelected.not().or(hasWriteAccess.not()).or(canManageSlides));

        MenuItem miTile = new MenuItem("Tile");
        miTile.setOnAction(e -> tileSlide());
        miTile.disableProperty().bind(Bindings.and(slideSelected, isTiled).or(slideSelected.not()));

        MenuItem miCopyID = new MenuItem("Copy ID");
        miCopyID.setOnAction(e -> copySlideID());

        MenuItem miCopyURL = new MenuItem("Copy URL");
        miCopyURL.setOnAction(e -> copySlideURL());

        MenuItem miViewProperties = new MenuItem("View properties");
        miViewProperties.setOnAction(e -> viewProperties());

        MenuButton menuMore = new MenuButton("More ...");
        menuMore.getItems().addAll(
            miRename, miDelete, miTile,
            new SeparatorMenuItem(),
            miCopyID, miCopyURL, miViewProperties
        );

        menuMore.disableProperty().bind(slideSelected.not());

        Button btnUpload = new Button("Import slide");
        btnUpload.setOnAction(e -> uploadSlide());
        btnUpload.disableProperty().bind(canManageSlides);

        GridPane paneButtons = GridPaneUtils.createColumnGridControls(
            btnAddRemote, btnAddLocal, btnOpen,
            new Separator(Orientation.VERTICAL),
            menuMore,
            new Separator(Orientation.VERTICAL),
            btnUpload
        );

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
            urls.add(EduAPI.getHost() + EduAPI.e(slide.getId()) + "#" + EduAPI.e(slide.getName()));
        });

        // Only add slides which have its' checkbox selected.
        // If no slides have a checked checkbox, we'll add the slide which is just selected
        if (urls.isEmpty()) {
            if (table.getSelectionModel().getSelectedItem() == null) {
                return;
            }

            ExternalSlide selected = table.getSelectionModel().getSelectedItem();
            urls.add(EduAPI.getHost() + EduAPI.e(selected.getId()) + "#" + EduAPI.e(selected.getName()));
        }

        dialog.close();

        Platform.runLater(() -> {
            // TODO: Add ProgressDialog
            for (String url : urls) {
                try {
                    ProjectCommands.addSingleImageToProject(
                        qupath.getProject(),
                        new EduImageServer(URI.create(url)),
                        ImageData.ImageType.OTHER
                    );
                } catch (IOException e) {
                    logger.error("Error while adding slide", e);

                    Dialogs.showErrorNotification(
                        "Error while adding slide",
                        "See log for possibly additional information."
                    );
                }
            }

            qupath.refreshProject();
        });
    }

    private void copySlideURL() {
        Dialogs.showInputDialog("Slide URL", "", EduAPI.getHost() + table.getSelectionModel().getSelectedItem().getId());
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
        openSlide(slide.getId());
    }

    public static void openSlide(String slideId) {
        if (qupath.getProject() != null) {
            ButtonType closeProject = new ButtonType("Close lesson and continue", ButtonBar.ButtonData.FINISH);
            ButtonType addToProject = new ButtonType("Add slide to lesson",       ButtonBar.ButtonData.OK_DONE);


            Optional<ButtonType> confirm = Dialogs.builder()
                .title("Proceed with adding slide to lesson")
                .contentText(
                    "Opening an slide with a lesson open will try to add that slide to the current lesson." +
                    "\n\n" +
                    "Do you wish to close your lesson and continue or add this slide to your current lesson?"
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
            try {
                qupath.openImage(qupath.getViewer(), EduAPI.getHost() + slideId, true, true);
                // TODO: qupath.getTabbedPanel().getSelectionModel().select(1);

                // Loading a slide will prompt to set ImageType. This marks ImageData as changed prompts and prompts pointlessly to save changes.
                qupath.getImageData().setChanged(false);
            } catch (IOException e) {
                Dialogs.showErrorNotification("Error while opening image", e);
            }
        });
    }

    private void viewProperties() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();
        var properties = EduAPI.getSlideProperties(slide.getId());

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
        propertiesTable.getItems().addAll(new Gson().fromJson(properties.get(), HashMap.class).entrySet());
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
        File file = FileChoosers.promptForFile("Select slide");

        if (file == null) {
            return;
        }

        try {
//            ImageServerBuilder<?> openSlideBuilder = ImageServerProvider.getInstalledImageServerBuilders().stream().filter(
//                imageServerBuilder -> imageServerBuilder.getName().equalsIgnoreCase("OpenSlide builder")
//            ).collect(MoreCollectors.onlyElement());
//
//            ImageServerBuilder.UriImageSupport<?> support = openSlideBuilder.checkImageSupport(file.toURI());
//
//            if (support.getSupportLevel() == 0) {
//                Dialogs.showWarningNotification("Error uploading slide", "Given file is not supported by OpenSlide.");
//                return;
//            }

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

            qupath.getThreadPoolManager().submitShortTask(task);
            progress.showAndWait();

            Dialogs.showMessageDialog(
                "Successfully uploaded slide",
                "The slide was successfully uploaded but is pending processing. Processing can take up to 30 minutes." +
                "\n\n" +
                "You can view your slide in a few minutes but it is missing higher magnifications until the processing is complete. "
            );

            refreshDialog();
        } catch (NoSuchElementException e) {
            Dialogs.showErrorNotification("Missing OpenSlide", "Please install the OpenSlide extension to import slides.");
        }/* catch (IOException e) {
            logger.error("Error while reading file", e);
        }*/
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


    private void tileSlide() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();

        Result result = EduAPI.submitSlideForTiling(slide.getId());

        if (result == Result.OK) {
            Dialogs.showInfoNotification(
                "Success",
                "Successfully submitted slide for tiling. Once tiling is completed, slide will be marked as tiled and is ready to be used."
            );
        } else {
            Dialogs.showErrorNotification(
                "Error",
                "Error while submitting slide for tiling -- slide might already be queued for tiling; please retry later."
            );
        }
    }

    private static class UploadSlideTask extends Task<Void> {

        private final File file;
        private final String filename;
        private final long fileSize;

        private int chunkIndex = 0;
        private final long chunks;

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
                    var result = EduAPI.uploadSlideChunk(
                        filename,
                        fileSize,
                        Arrays.copyOf(buffer, read),
                        CHUNK_BUFFER_SIZE,
                        chunkIndex
                    );

                    if (result != Result.OK) {
                        updateMessage("Error while uploading slide, see log for possibly more details.");
                        return null;
                    }

                    double progress = (chunkIndex * 1.0 / chunks) * 100;
                    updateMessage(String.format("Uploading %.1f%%", progress));
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
