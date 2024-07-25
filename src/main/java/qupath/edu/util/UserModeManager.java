package qupath.edu.util;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeView;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduProject;
import qupath.edu.gui.dialogs.SimpleAnnotationPane;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Used to manage various aspects of user mode for QuPath Edu.
 */
public class UserModeManager {

    private final Logger logger = LoggerFactory.getLogger(UserModeManager.class);

    private final QuPathGUI qupath;

    private final SimpleObjectProperty<UserMode> userMode = new SimpleObjectProperty<>(UserMode.STUDYING);

    /**
     * Stores the pre-editing image data which can be restored.
     */
    private final ByteArrayOutputStream imageDataBackup = new ByteArrayOutputStream(0);

    public UserModeManager(QuPathGUI qupath) {
        this.qupath = qupath;

        qupath.setReadOnly(getUserMode().isReadOnly());

        qupath.imageDataProperty().addListener(this::onImageDataChanged);
        qupath.getToolManager().setToolSwitchingEnabled(getUserMode().isToolsEnabled());
        qupath.getToolBar().managedProperty().bind(qupath.readOnlyProperty().not());

        userMode.addListener(this::onUserModeChanged);

        // todo: add support for when not logged in to server after refactoring EduAPI

        this.replaceAnnotationsPane();
        this.disableButtons();
    }

    public SimpleObjectProperty<UserMode> userModeProperty() {
        return userMode;
    }

    public UserMode getUserMode() {
        return userMode.get();
    }

    private void replaceAnnotationsPane() {
        var oldTabs = FXCollections.observableArrayList(qupath.getAnalysisTabPane().getTabs());

        var annotations = oldTabs.get(2);
        var oldAnnotationPane = annotations.getContent();
        var newAnnotationPane = new SimpleAnnotationPane(qupath).getPane();

        var slides = oldTabs.get(0);

        var newTabs = List.of(slides, annotations);

        annotations.setContent(newAnnotationPane);
        qupath.getAnalysisTabPane().getTabs().setAll(newTabs);

        userMode.addListener((obs, oldMode, newMode) -> {
            if (newMode == UserMode.ANALYSING) {
                annotations.setContent(oldAnnotationPane);
                qupath.getAnalysisTabPane().getTabs().setAll(oldTabs);
            } else {
                annotations.setContent(newAnnotationPane);
                qupath.getAnalysisTabPane().getTabs().setAll(newTabs);
            }
        });
    }

    /**
     * Backup new image data if editing mode is enabled.
     */
    private void onImageDataChanged(ObservableValue<? extends ImageData<BufferedImage>> obs, ImageData<BufferedImage> oldData, ImageData<BufferedImage> imageData) {
        if (imageData == null || qupath.isReadOnly()) {
            return;
        }

        logger.debug("Switching slides with edit mode enabled -- creating a backup");
        backupCurrentImageData();
    }

    private void onUserModeChanged(ObservableValue<? extends UserMode> obs, UserMode oldMode, UserMode newMode) {
        logger.debug("Changing user modes from {} to {}", oldMode, newMode);

        if (newMode.isToolsEnabled()) {
            this.backupCurrentImageData();

            PathPrefs.imageTypeSettingProperty().set(PathPrefs.ImageTypeSetting.PROMPT);
        } else {
            this.promptToSaveOrRestoreImageData();

            PathPrefs.imageTypeSettingProperty().set(PathPrefs.ImageTypeSetting.NONE);
            qupath.getToolManager().setSelectedTool(PathTools.MOVE);
            qupath.getViewer().setActiveTool(PathTools.MOVE);
        }

        qupath.getToolManager().setToolSwitchingEnabled(newMode.isToolsEnabled());
        qupath.setReadOnly(newMode.isReadOnly());
    }

    /**
     * Disable various buttons based on whether QuPath is in read-only mode. This does not include all actions
     * such as those related to annotations, because they're unavailable using the QuPath API.
     * todo: add support for when not logged in to server after refactoring EduAPI
     */
    private void disableButtons() {
        disableGUIButtons();
        disableContextMenuButtons();
        disableMainToolBarButtons();
    }

    private void disableContextMenuButtons() {
        List<String> menuItemsToDisable = List.of("Remove image(s)", "Delete image(s)", "Rename image", "Refresh thumbnail",
                "Edit description", "Add metadata", "Duplicate image(s)");

        TreeView<Object> tree = ReflectionUtil.getProjectBrowserTree();
        List<MenuItem> items = tree.getContextMenu().getItems();

        for (MenuItem item : items) {
            if (item == null || item.getText() == null) {
                continue;
            }

            if (menuItemsToDisable.contains(item.getText())) {
                item.disableProperty().bind(qupath.readOnlyProperty());
            }
        }
    }

    private void disableGUIButtons() {
        String[] actionsToDisable = { "Create project", "Add images", "Edit project metadata", "Check project URIs" };

        for (String text : actionsToDisable) {
            Action action = qupath.lookupActionByText(text);

            if (action != null) {
                action.disabledProperty().bind(qupath.readOnlyProperty());
            }
        }
    }

    private void disableMainToolBarButtons() {
        String[] menuItemsToDisable = { "Tools", "Objects", "TMA", "Measure", "Automate", "Analyze", "Classify" };

        for (var menuItemName : menuItemsToDisable) {
            var menu = qupath.getMenu(menuItemName, false);

            if (menu == null) {
                logger.warn("Missing menu {} from main toolbar", menuItemName);
            } else {
                menu.visibleProperty().bind(qupath.readOnlyProperty().not());
            }
        }
    }

    private void promptToSaveOrRestoreImageData() {
        var project = qupath.getProject();
        var imageData = qupath.getImageData();

        if (!(project instanceof EduProject)) return;
        if (imageData == null || !imageData.isChanged()) return;

        var SAVE_CHANGES    = "Save changes";
        var DISCARD_CHANGES = "Discard changes";
        var CANCEL          = "Cancel";

        var choice = Dialogs.builder()
                .title("Confirm")
                .contentText("Do you wish to save or discard changes?")
                .buttons(SAVE_CHANGES, DISCARD_CHANGES, CANCEL)
                .build()
                .showAndWait()
                .orElseGet(() -> new ButtonType(CANCEL))
                .getText();

        if (choice.equals(SAVE_CHANGES)) {
            saveImageData();
        } else if (choice.equals(DISCARD_CHANGES)) {
            restorePreviousImageData();
        }
    }

    /**
     * Save current changes to project. We cannot use {@link Commands#promptToSaveImageData} because {@link EduProject}
     * is meant to be saved on the server, so we cannot have the chance of prompting the user to save an EduProject
     * to a disk -- at least for now. EduProjects could be technically saved locally in the future also.
     */
    private void saveImageData() {
        var project = qupath.getProject();
        var imageData = qupath.getImageData();

        if (imageData == null) return;

        var entry = project.getEntry(imageData);

        if (entry == null) {
            logger.debug("Tried to save changes but could not find entry {}", imageData);
            return;
        }

        try {
            entry.saveImageData(imageData);
            project.syncChanges();
        } catch (IOException e) {
            Dialogs.showErrorNotification("Error while saving changes", e.getMessage());
        }
    }

    private void backupCurrentImageData() {
        var imageData = qupath.getImageData();

        if (imageData == null) return;

        try {
            imageDataBackup.reset();

            imageData.setChanged(false);
            PathIO.writeImageData(imageDataBackup, imageData);
        } catch (IOException e) {
            Dialogs.showErrorNotification("Error when backing-up image data", e);

            // todo: restore previous state
        }
    }

    private void restorePreviousImageData() {
        // This happens when the user hasn't opened any images and tries to restore changes.
        if (imageDataBackup.size() == 0) return;

        try {
            var data = PathIO.readImageData(new ByteArrayInputStream(imageDataBackup.toByteArray()), null, null, BufferedImage.class);

            // todo: multiple viewers?
            qupath.getViewer().setImageData(data);
        } catch (IOException e) {
            Dialogs.showErrorNotification("Error when restoring image data", e);
        }
    }
}
