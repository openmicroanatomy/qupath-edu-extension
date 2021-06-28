package qupath.edu.util;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Used to manage various aspects of edit mode for QuPath Edu.
 */
public class EditModeManager {

    private final Logger logger = LoggerFactory.getLogger(EditModeManager.class);

    private final SimpleBooleanProperty editModeEnabled = new SimpleBooleanProperty(false);

    /**
     * Stores the pre-editing image data which can be restored.
     */
    private final ByteArrayOutputStream imageDataBackup = new ByteArrayOutputStream(0);

    public SimpleBooleanProperty editModeEnabledProperty() {
        return editModeEnabled;
    }

    public boolean isEditModeEnabled() {
        return editModeEnabled.get();
    }

    public boolean isEditModeDisabled() {
        return !(editModeEnabled.get());
    }

    public void toggleEditMode() {
        setEditModeEnabled(!(isEditModeEnabled()));
    }

    public void setEditModeEnabled(boolean enabled) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        if (enabled) {
            editModeEnabledProperty().set(true);
            qupath.setReadOnly(false);

            if (qupath.getImageData() != null) {
                try {
                    imageDataBackup.reset();
                    PathIO.writeImageData(imageDataBackup, qupath.getImageData());
                } catch (IOException e) {
                    Dialogs.showErrorNotification("Error when backing up image data", e);
                }
            }
        } else {
            var choice = Dialogs.builder()
                    .title("Confirm")
                    .contentText("Do you wish to save changes or restore changes?")
                    .buttons("Save", "Restore", "Cancel")
                    .build()
                    .showAndWait()
                    .orElseGet(() -> new ButtonType("Cancel"))
                    .getText();

            if (choice.equals("Save")) {
                var project = qupath.getProject();
                var imageData = qupath.getImageData();

                try {
                    if (imageData != null) {
                        project.getEntry(imageData).saveImageData(imageData);
                    }

                    project.syncChanges();
                } catch (IOException e) {
                    Dialogs.showErrorNotification("Error while saving project", e.getMessage());
                }

                qupath.setReadOnly(true);
                editModeEnabledProperty().set(false);
            } else if (choice.equals("Restore")) {
                restoreImageData();
                qupath.setReadOnly(true);
                editModeEnabledProperty().set(false);
            }
        }
    }

    public void restoreImageData() {
        try {
            QuPathGUI.getInstance().getViewer().setImageData(PathIO.readImageData(new ByteArrayInputStream(imageDataBackup.toByteArray()), null, null, BufferedImage.class));
        } catch (IOException e) {
            logger.error("Error when restoring image data", e);
        }
    }

    public void backupImageData(ImageData<BufferedImage> imageData) {
        try {
            this.imageDataBackup.reset();
            PathIO.writeImageData(this.imageDataBackup, imageData);
        } catch (IOException e) {
            logger.error("Error when backing up image data", e);
        }
    }
}
