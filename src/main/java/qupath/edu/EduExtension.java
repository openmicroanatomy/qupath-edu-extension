package qupath.edu;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.action.Action;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.api.EduAPI;
import qupath.edu.gui.Browser;
import qupath.edu.gui.UserModeListCell;
import qupath.edu.gui.buttons.IconButtons;
import qupath.edu.gui.dialogs.*;
import qupath.edu.tours.SlideTour;
import qupath.edu.util.EditModeManager;
import qupath.edu.util.ReflectionUtil;
import qupath.edu.util.UserMode;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.projects.Project;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static qupath.lib.gui.actions.ActionTools.createAction;
import static qupath.lib.gui.actions.ActionTools.createMenuItem;

/**
 * TODO:
 *  - ArrowTool and its respective ROI
 *  - Tons of minor changes
 *  - Figure out why "Save as" syncs changes but not "Save"
 *
 */
public class EduExtension implements QuPathExtension, GitHubProject {

    private static final Logger logger = LoggerFactory.getLogger(EduExtension.class);

    @Nonnull private static EduExtension instance;

    private QuPathGUI qupath;

    private EditModeManager editModeManager;
    private static final SimpleBooleanProperty noWriteAccess = new SimpleBooleanProperty(true);
    private static final Browser projectInformation = new Browser();
    private final TabPane tabbedPanel = new TabPane();

    @Override
    public void installExtension(QuPathGUI qupath) {
        instance = this;

        this.qupath = qupath;
        this.editModeManager = new EditModeManager(qupath);

        if (QuPathGUI.getVersion().getMinor() < 5) {
            var confirm = Dialogs.showYesNoDialog(
                "Unverified version",
                "QuPath Edu has not been tested with this version of QuPath. Do you wish to proceed?"
            );

            if (!confirm) {
                return;
            }
        }

        initializePreferences();
        notifyWhenEnablingOrDisablingExtension();

        if (!EduOptions.extensionEnabled().get()) {
            return;
        }

        initializeMenus();

        replaceAnnotationsPane();
        replaceViewer();
        replaceProjectBrowserButtons();
        registerSlideTours();

        onProjectChange();
        onSlideChange();
        onImageDataChange();
        onEditModeToggled();

        registerButtons();
        disableStartupMessage();

        // Run on a separate thread, because extension initialization happens in QuPath's
        // main thread and our dialogs can interfere with its' initialization.
        Platform.runLater(() -> {
            // Perform first time setup if host is undefined.
            if (EduOptions.host().isNull().get()) {
                FirstTimeSetup.showDialog();
            }

            EduAPI.setHost(EduOptions.host().get());
            EduOptions.host().addListener(((obs, oldHost, newHost) -> EduAPI.setHost(newHost)));

            if (EduOptions.showLoginDialogOnStartup().get()) {
                showWorkspaceOrLoginDialog();
            }
        });
    }

    public static EduExtension getInstance() {
        return instance;
    }

    private void onEditModeToggled() { // todo: move to edit mode manager
        editModeManager.editModeEnabledProperty().addListener((observable, oldValue, enabled) -> {
            if (enabled) {
                PathPrefs.imageTypeSettingProperty().set(PathPrefs.ImageTypeSetting.PROMPT);
            } else {
                PathPrefs.imageTypeSettingProperty().set(PathPrefs.ImageTypeSetting.NONE);
                qupath.getToolManager().setSelectedTool(PathTools.MOVE);
                qupath.getViewer().setActiveTool(PathTools.MOVE);
            }

            qupath.getToolManager().setToolSwitchingEnabled(enabled);
        });

        // Toggle edit mode so that tools get disabled / enabled
        editModeManager.editModeEnabledProperty().set(!editModeManager.editModeEnabledProperty().get());
        editModeManager.editModeEnabledProperty().set(!editModeManager.editModeEnabledProperty().get());
    }

    private void onSlideChange() {
        qupath.imageDataProperty().addListener((obs, o, n) -> {
            qupath.getAnalysisTabPane().getSelectionModel().select(0);
            tabbedPanel.getSelectionModel().select(1);
        });
    }

    private void onProjectChange() {
        qupath.projectProperty().addListener((obs, oldProject, newProject) -> {
            var MISSING = "No information available for this lesson";
            
            if (newProject == null) {
                setProjectInformation("No lesson open");
            } else if (newProject instanceof EduProject project) {
                String projectInformation = project.getProjectInformation();

                setProjectInformation(Objects.requireNonNullElse(projectInformation, MISSING));
            } else {
                setProjectInformation(MISSING);
            }
        });
    }

    private void onImageDataChange() {
        qupath.imageDataProperty().addListener((observable, oldValue, imageData) -> {
            if (imageData == null || editModeManager.isEditModeDisabled()) {
                return;
            }

            logger.debug("Switching slides with edit mode enabled -- creating a backup");
            editModeManager.backupImageData(imageData);
        });
    }

    @Override
    public String getName() {
        return "QuPath Education";
    }

    @Override
    public String getDescription() {
        return "Use QuPath for studying!";
    }

    @Override
    public GitHubRepo getRepository() {
        return GitHubRepo.create("QuPath Edu Extension", "openmicroanatomy", "qupath-extension");
    }

    @Override
    public Version getQuPathVersion() {
        return Version.parse("0.5.0");
    }

    @Override
    public Version getVersion() {
        return Version.parse("1.0.1");
    }

    public EditModeManager getEditModeManager() {
        return editModeManager;
    }

    private void initializeMenus() {
        Action action = createAction(ProjectDescriptionEditorCommand::openDescriptionEditor, "Edit lesson information");
        action.disabledProperty().bind(editModeManager.editModeEnabledProperty().not());

        qupath.getMenu("File>Project...", false).getItems().add(7,
            createMenuItem(action)
        );

        qupath.getMenu("QuPath Edu", true).getItems().addAll(
            createMenuItem(createAction(ExternalSlideManager::showExternalSlideManager, "Manage slides")),
            createMenuItem(createAction(BackupManager::showBackupManagerPane, "Manage backups")),
            createMenuItem(createAction(RemoteUserManager::showManagementDialog, "Manage users")),
            createMenuItem(createAction(OrganizationManager::showOrganizationManager, "Manage organizations")),
            createMenuItem(createAction(WorkspacePermissionManager::showDialog, "Manage permissions")),
            createMenuItem(createAction(EduExtension::showWorkspaceOrLoginDialog, "Show workspaces")),
            createMenuItem(createAction(this::checkSaveChanges, "Sync changes"))
        );

        ComboBox<UserMode> cbModes = new ComboBox<>();
        cbModes.setItems(FXCollections.observableArrayList(UserMode.STUDYING, UserMode.ANALYSING, UserMode.EDITING));
        cbModes.setButtonCell(new UserModeListCell(true));
        cbModes.setCellFactory(f -> new UserModeListCell(false));
        cbModes.getSelectionModel().select(0);
        cbModes.setPadding(new Insets(0));
        cbModes.setStyle("-fx-background-color: transparent; -fx-text-fill: #fff; -fx-background-insets: 0; -fx-background-radius: 0; ");
        getEditModeManager().userModeProperty().bind(cbModes.getSelectionModel().selectedItemProperty());

        Menu customMenu = new Menu("", cbModes);
        customMenu.setStyle("-fx-padding: 0;");
        customMenu.getStyleClass().add("custom-menu");

        qupath.getMenuBar().getStylesheets().add(EduExtension.class.getClassLoader().getResource("css/custom-menu.css").toExternalForm());
        qupath.getMenuBar().getMenus().add(customMenu);
    }

    private void initializePreferences() {
        PreferencePane prefs = qupath.getPreferencePane();

        prefs.addPropertyPreference(EduOptions.extensionEnabled(), Boolean.class,
            "Extension Enabled",
            "Edu",
            "Restart needed for changes to take effect");

        prefs.addPropertyPreference(EduOptions.host(), String.class,
            "Edu Host",
            "Edu",
            "Server used with QuPath Education");

        prefs.addPropertyPreference(EduOptions.showLoginDialogOnStartup(), Boolean.class,
            "Show login dialog on startup",
            "Edu",
            "If enabled, opens the login dialog on startup.");

        prefs.addPropertyPreference(EduOptions.checkForUpdatesOnStartup(), Boolean.class,
            "Check for updates on startup",
            "Edu",
            "If enabled, checks for updates on startup.");

        // Hide annotation names in viewer by default for visual reasons
        qupath.getOverlayOptions().showNamesProperty().set(false);
    }

    public static void setWriteAccess(boolean hasWriteAccess) {
        noWriteAccess.set(!hasWriteAccess);
    }

    public static void setProjectInformation(String information) {
        projectInformation.setContent(information);
    }

    private void replaceAnnotationsPane() {
        var oldTabs = FXCollections.observableArrayList(qupath.getAnalysisTabPane().getTabs());

        var annotations = oldTabs.get(2);
        var oldAnnotationPane = annotations.getContent();
        var newAnnotationPAne = new SimpleAnnotationPane(qupath).getPane();

        var slides = oldTabs.get(0);

        var newTabs = List.of(slides, annotations); //FXCollections.unmodifiableObservableList(FXCollections.observableArrayList(slides, annotations));

        //qupath.getAnalysisTabPane().getTabs().setAll(newTabs);

        // todo: move to edit mode manager
        editModeManager.userModeProperty().addListener((obs, oldMode, newMode) -> {
            if (newMode == UserMode.ANALYSING) {
                annotations.setContent(oldAnnotationPane);
                qupath.getAnalysisTabPane().getTabs().setAll(oldTabs);
            } else {
                annotations.setContent(newAnnotationPAne);
                qupath.getAnalysisTabPane().getTabs().setAll(newTabs);
            }
        });

//        editModeManager.editModeEnabledProperty().addListener((observable, oldMode, newValue) -> {
//            if (newValue) {
//                logger.info(oldTabs.toString());
//                annotations.setContent(oldAnnotationPane);
//                qupath.getAnalysisTabPane().getTabs().setAll(oldTabs);
//            } else {
//                logger.info(newTabs.toString());
//                annotations.setContent(newAnnotationPAne);
//                qupath.getAnalysisTabPane().getTabs().setAll(newTabs);
//            }
//        });
    }

    private void replaceViewer() {
        projectInformation.setOnMouseClicked(event -> {
            Project<BufferedImage> project = qupath.getProject();

            if (event.getClickCount() > 1 && project instanceof EduProject) {
                String projectId = ((EduProject) project).getId();

                if (getEditModeManager().isEditModeEnabled() && EduAPI.hasWritePermission(projectId)) {
                    ProjectDescriptionEditorCommand.openDescriptionEditor();
                }
            }
        });

        tabbedPanel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabbedPanel.getTabs().add(new Tab("Lesson Information", projectInformation));
        tabbedPanel.getTabs().add(new Tab("Viewer", ReflectionUtil.getMainViewerPane()));

        ReflectionUtil.setMainViewerPane(tabbedPanel);

        // Refreshes the pane and makes the tabs visible
        ReflectionUtil.setAnalysisPaneVisible(false);
        ReflectionUtil.setAnalysisPaneVisible(true);
    }

    private void replaceProjectBrowserButtons() {
        ProjectBrowser projectBrowser = ReflectionUtil.getProjectBrowser();

        Button btnCreate = ActionTools.createButton(
            ActionTools.createAction(EduExtension::showWorkspaceOrLoginDialog, "Create lesson")
        );
        btnCreate.disableProperty().bind(editModeManager.editModeEnabledProperty().not());

        Button btnOpen = ActionTools.createButton(
            ActionTools.createAction(EduExtension::showWorkspaceOrLoginDialog, "Open lesson")
        );

        Button btnAdd = ActionTools.createButton(
            ActionTools.createAction(ExternalSlideManager::showExternalSlideManager, "Add images")
        );
        btnAdd.disableProperty().bind(editModeManager.editModeEnabledProperty().not().or(qupath.projectProperty().isNull()));

        GridPane paneButtons = GridPaneUtils.createColumnGridControls(btnCreate, btnOpen, btnAdd);
        paneButtons.prefWidthProperty().bind(projectBrowser.getPane().widthProperty());
        paneButtons.setPadding(new Insets(5, 5, 5, 5));
        ((BorderPane) projectBrowser.getPane()).setTop(paneButtons);
    }

    private void registerSlideTours() {
        QuPathViewer viewer = qupath.getViewer();

        if (!(viewer instanceof QuPathViewerPlus)) {
            return;
        }

        SlideTour slideTour = new SlideTour(viewer);
        Node slideTourNode = slideTour.getNode();

        ReflectionUtil.getViewerBasePane((QuPathViewerPlus) viewer).getChildren().add(slideTourNode);

        AnchorPane.setTopAnchor(slideTourNode, 10d);
        AnchorPane.setLeftAnchor(slideTourNode, 10d);

        viewer.addViewerListener(slideTour);
    }

    private void checkSaveChanges() {
        if (qupath.getProject() instanceof EduProject) {
            try {
                qupath.getProject().syncChanges();
            } catch (IOException e) {
                Dialogs.showErrorMessage("Sync error", "Error while syncing project");
            }
        }
    }

    /**
     * Notify and prompt the user to exit QuPath when enabling or disabling the extension.
     */
    private void notifyWhenEnablingOrDisablingExtension() {
        EduOptions.extensionEnabled().addListener((obs, o, isEnabled) -> {
            var exit = Dialogs.showYesNoDialog(
                "Restart required",
                "QuPath must be restarted after " + (isEnabled ? "enabling" : "disabling") + " the Edu Extension." +
                "\n\n" +
                "Exit QuPath now?"
            );

            if (exit) {
                Platform.exit();
            }
        });
    }

    private void registerButtons() {
        PopOver infoPopOver = new PopOver(EditModeInfoPopOverDialog.getPane());
        infoPopOver.setDetachable(false);
        infoPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);

        Button btnEditModeInfo = IconButtons.createIconButton(FontAwesome.Glyph.QUESTION, 10);
        btnEditModeInfo.setFont(Font.font(10));
        btnEditModeInfo.setOnAction(a -> infoPopOver.show(btnEditModeInfo));

        qupath.getToolBar().getItems().addAll(new Separator(), btnEditModeInfo);
    }

    private void disableStartupMessage() {
        PathPrefs.showStartupMessageProperty().set(false);
    }

    public static void showWorkspaceOrLoginDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(EduExtension::showWorkspaceOrLoginDialog);
            return;
        }

        try {
            if (EduAPI.getAuthType() == EduAPI.AuthType.UNAUTHENTICATED) {
                RemoteServerLoginManager.showLoginDialog();
            } else {
                WorkspaceManager.showWorkspace(QuPathGUI.getInstance());
            }
        } catch (Exception e) {
            logger.error("Error when connecting to server", e);

            String[] choices = { "Cancel", "Change server", "Retry connection" };

            var dialog = Dialogs.builder()
                .title("Error when connecting to " + EduAPI.getHost())
                .contentText("Please check your internet connection and that you're connecting to the correct server.")
                .buttons(choices)
                .build();

            var response = dialog.showAndWait();

            EduAPI.logout();

            if (response.isPresent()) {
                var text = response.orElse(ButtonType.CLOSE).getText();

                if (text.equals(choices[1])) {
                    FirstTimeSetup.showDialog();
                    showWorkspaceOrLoginDialog();
                } else if (text.equals(choices[2])) {
                    showWorkspaceOrLoginDialog();
                }
            }
        }
    }
}
