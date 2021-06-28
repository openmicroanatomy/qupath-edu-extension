package qupath.edu.gui.dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.exceptions.HttpException;
import qupath.edu.api.EduAPI;
import qupath.edu.models.VersionHistory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.awt.*;
import java.net.URI;

public class UpdateManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private QuPathGUI qupath = QuPathGUI.getInstance();

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    private static VersionHistory.Release latestRelease;

    public static void checkForUpdates() {
        try {
            VersionHistory versionHistory = EduAPI.getVersionHistory();
            latestRelease = versionHistory.getLatest();

            if (latestRelease.getVersion().compareTo(EduExtension.getExtensionVersion()) > 0) {
                showDialog();
            }
        } catch (HttpException e) {
            Dialogs.showErrorNotification("Error when checking for Edu updates", e);
        }
    }

    private static void showDialog() {
        UpdateManager updateManager = new UpdateManager();

        dialog = Dialogs.builder()
                .title("QuPath Edu Updater")
                .content(updateManager.getPane())
                .build();

        dialog.getDialogPane().getStylesheets().add(UpdateManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
        dialog.setResult(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    public synchronized BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        pane = new BorderPane();

        VBox vbox = new VBox(10);

        /* Header */

        Text title = new Text("New version of QuPath Edu is available. Do you want to update now?");

        /* Release notes */

        Label releaseNotes = new Label(latestRelease.getReleaseNotes());
        releaseNotes.setWrapText(true);
        releaseNotes.setMaxWidth(500);

        /* Buttons */

        Button btnDownload = new Button("Download now");
        btnDownload.setOnAction(a -> openDownloadPage());

        Button btnExit = new Button("Remind me later");
        btnExit.setOnAction(a -> dialog.close());

        Button btnSettings = new Button("Settings ...");
        btnSettings.setOnAction(qupath.lookupActionByText("Preferences...")); // TODO: Expand "Edu" section directly

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(btnSettings, btnExit, btnDownload);

        ButtonBar.setButtonData(btnDownload, ButtonBar.ButtonData.RIGHT);
        ButtonBar.setButtonData(btnExit, ButtonBar.ButtonData.RIGHT);
        ButtonBar.setButtonData(btnSettings, ButtonBar.ButtonData.LEFT);

        /* Pane */

        vbox.getChildren().addAll(title, new Separator(), releaseNotes, buttonBar);

        pane.setCenter(vbox);
    }

    private void openDownloadPage() {
        dialog.close();

        try {
            Desktop.getDesktop().browse(URI.create(latestRelease.getDownloadUrl()));
        } catch (Exception e) {
            Dialogs.showInputDialog("Error", "Could not open download page", latestRelease.getDownloadUrl());
            logger.error("Could not open download page", e);
        }
    }
}
