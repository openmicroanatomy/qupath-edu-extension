package qupath.edu.gui.dialogs;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import qupath.edu.EduExtension;
import qupath.edu.api.EduAPI;
import qupath.edu.models.ExternalBackup;
import qupath.edu.models.ExternalProject;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

import java.util.Collections;

public class BackupManager {

    private BackupManager() {}

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;
    private TableView<ExternalBackup> table;

    public static void showBackupManagerPane() {
        BackupManager manager = new BackupManager();

        dialog = Dialogs.builder()
                .title("Backup Manager")
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
        table.setPlaceholder(new Text("No backups, none match search criteria or no permissions."));

        TableColumn<ExternalBackup, String> backupNameColumn = new TableColumn<>("Project / File name");
        backupNameColumn.setCellValueFactory(new PropertyValueFactory<>("readable"));
        backupNameColumn.setReorderable(false);

        TableColumn<ExternalBackup, String> filenameColumn = new TableColumn<>("Filename");
        filenameColumn.setCellValueFactory(new PropertyValueFactory<>("filename"));
        filenameColumn.setReorderable(false);

        TableColumn<ExternalBackup, String> timestampColumn = new TableColumn<>("Timestamp");
        timestampColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedTimestamp()));
        timestampColumn.setReorderable(false);
        timestampColumn.setSortType(TableColumn.SortType.DESCENDING);

        table.getColumns().addAll(backupNameColumn, filenameColumn, timestampColumn);

        table.setItems(FXCollections.observableArrayList(EduAPI.getAllBackups().orElse(Collections.emptyList())));

        /* Bindings */

        BooleanBinding backupSelected = table.getSelectionModel().selectedItemProperty().isNull();

        /* Buttons */

        Button btnRestore = new Button("Restore Backup");
        btnRestore.setOnAction(e -> restoreBackup());
        btnRestore.disableProperty().bind(backupSelected);

        Button btnPreview = new Button("Preview Backup");
        btnPreview.setOnAction(e -> previewBackup());
        btnPreview.disableProperty().bind(backupSelected);

        GridPane buttons = PaneTools.createColumnGridControls(btnRestore, btnPreview);
        buttons.setHgap(5);

        /* Pane */

        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        pane = new BorderPane();
        pane.setPrefWidth(800);
        pane.setPrefHeight(400);
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
            "You're currently viewing a backup of a project. Any changes you make here will be lost once you close the project." +
            "\n\n" +
            "If you wish to restore this backup, you can do that via the Backup Manager."
        );
    }
}
