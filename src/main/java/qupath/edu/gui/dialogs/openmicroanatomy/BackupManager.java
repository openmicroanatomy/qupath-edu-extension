package qupath.edu.gui.dialogs.openmicroanatomy;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.api.EduAPI;
import qupath.edu.gui.dialogs.WorkspaceManager;
import qupath.edu.models.ExternalBackup;
import qupath.edu.models.ExternalProject;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

public class BackupManager {

    private BackupManager() {}

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;
    private TableView<ExternalBackup> table;

    public static void show() {
        BackupManager manager = new BackupManager();

        dialog = Dialogs.builder()
                .title("Backup manager")
                .content(manager.getPane())
                .buttons(ButtonType.CLOSE)
                .resizable()
                .build();

        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
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
        table.setPlaceholder(new Text("No backups or no permissions."));

        TableColumn<ExternalBackup, String> backupNameColumn = new TableColumn<>("Lesson");
        backupNameColumn.setCellValueFactory(new PropertyValueFactory<>("readable"));
        backupNameColumn.setReorderable(false);

        TableColumn<ExternalBackup, String> filenameColumn = new TableColumn<>("Filename");
        filenameColumn.setCellValueFactory(new PropertyValueFactory<>("filename"));
        filenameColumn.setReorderable(false);

        TableColumn<ExternalBackup, String> timestampColumn = new TableColumn<>("Timestamp");
        timestampColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedTimestamp()));
        timestampColumn.setReorderable(false);
        timestampColumn.setSortType(TableColumn.SortType.DESCENDING);
        timestampColumn.setComparator((s1, s2) -> {
            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH.mm.ss");

            try {
                Date d1 = formatter.parse(s1);
                Date d2 = formatter.parse(s2);

                return Long.compare(d1.getTime(), d2.getTime());
            } catch (ParseException e) {
                logger.error("Invalid date format", e);
            }

            return -1;
        });

        table.getColumns().addAll(backupNameColumn, filenameColumn, timestampColumn);

        table.setItems(FXCollections.observableArrayList(EduAPI.getAllBackups().orElse(Collections.emptyList())));
        table.getSortOrder().add(timestampColumn);

        /* Bindings */

        BooleanBinding backupSelected = table.getSelectionModel().selectedItemProperty().isNull();

        /* Buttons */

        Button btnRestore = new Button("Restore Backup");
        btnRestore.setOnAction(e -> restoreBackup());
        btnRestore.disableProperty().bind(backupSelected);

        Button btnPreview = new Button("Preview Backup");
        btnPreview.setOnAction(e -> previewBackup());
        btnPreview.disableProperty().bind(backupSelected);

        GridPane buttons = GridPaneUtils.createColumnGridControls(btnRestore, btnPreview);
        buttons.setHgap(5);

        /* Pane */

        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        pane = new BorderPane();
        pane.setPrefWidth(800);
        pane.setPrefHeight(400);
        pane.setTop(new Text("Backups are removed automatically after one year."));
        pane.setCenter(table);
        pane.setBottom(buttons);
        pane.setPadding(new Insets(10));
    }

    private void restoreBackup() {
        ExternalBackup backup = table.getSelectionModel().getSelectedItem();

        var confirm = Dialogs.showConfirmDialog(
            "Restore backup",
            "Are you sure you wish to restore " + backup.getReadable() + " to the state that it was at " + backup.getFormattedTimestamp() +
            "\n\n" +
            "This action is irreversible."
        );

        if (!confirm) {
            return;
        }

        var success = EduAPI.restoreBackup(backup.getFilename(), backup.getTimestamp());

        if (success) {
            Dialogs.showInfoNotification(
                "Backup restored",
                "Successfully restored backup"
            );

            dialog.close();

            EduExtension.showWorkspaceOrLoginDialog();
        } else {
            Dialogs.showErrorNotification(
                "Backup restoring aborted",
                "Error while restoring backup. The error was logged and acted accordingly."
            );
        }
    }

    private void previewBackup() {
        ExternalBackup backup = table.getSelectionModel().getSelectedItem();

        ExternalProject project = new ExternalProject();
        project.setId(backup.getBaseName());
        project.setTimestamp(backup.getTimestamp());
        project.setName("Backup of " + backup.getReadable() + " at " + backup.getFormattedTimestamp());

        dialog.close();

        WorkspaceManager.loadProject(project);

        Dialogs.showMessageDialog(
            "Viewing a backup",
            "You're currently viewing a backup of a lesson. Any changes you make here will be lost once you close the lesson." +
            "\n\n" +
            "If you wish to restore this backup, you can do that via the Backup Manager."
        );
    }
}
