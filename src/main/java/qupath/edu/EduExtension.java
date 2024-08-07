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
import qupath.edu.gui.dialogs.openmicroanatomy.SlideManager;
import qupath.edu.tours.SlideTour;
import qupath.edu.util.UserModeManager;
import qupath.edu.util.ReflectionUtil;
import qupath.edu.util.UserMode;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.projects.Project;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

import static qupath.lib.gui.actions.ActionTools.createAction;
import static qupath.lib.gui.actions.ActionTools.createMenuItem;

public class EduExtension implements QuPathExtension, GitHubProject {

    private static final Logger logger = LoggerFactory.getLogger(EduExtension.class);

    @Nonnull private static EduExtension instance;

    private QuPathGUI qupath;

    private UserModeManager userModeManager;
    private static final SimpleBooleanProperty noWriteAccess = new SimpleBooleanProperty(true);
    private static final Browser projectInformation = new Browser("No lesson open");
    private final TabPane tabbedPanel = new TabPane();

    @Override
    public void installExtension(QuPathGUI qupath) {
        instance = this;

        this.qupath = qupath;

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

        this.userModeManager = new UserModeManager(qupath);

        initializeMenus();

        replaceViewer();
        replaceProjectBrowserButtons();
        registerSlideTours();

        onProjectChange();
        onSlideChange();

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
        return Version.parse("0.5.1");
    }

    @Override
    public Version getVersion() {
        return Version.parse("1.0.2");
    }

    public UserModeManager getUserModeManager() {
        return userModeManager;
    }

    private void initializeMenus() {
        Action action = createAction(ProjectDescriptionEditorCommand::openDescriptionEditor, "Edit lesson information");
        action.disabledProperty().bind(qupath.readOnlyProperty());

        qupath.getMenu("File>Project...", false).getItems().add(7,
            createMenuItem(action)
        );

        qupath.getMenu("QuPath Edu", true).getItems().addAll(
            createMenuItem(createAction(AdministrativeToolsDialog::show, "Administrative tools")),
            createMenuItem(createAction(EduExtension::showWorkspaceOrLoginDialog, "Show workspaces")),
            createMenuItem(createAction(this::checkSaveChanges, "Force sync changes"))
        );

        ComboBox<UserMode> cbUserMode = new ComboBox<>();
        cbUserMode.setItems(FXCollections.observableArrayList(UserMode.STUDYING, UserMode.ANALYSING, UserMode.EDITING));
        cbUserMode.setButtonCell(new UserModeListCell(true));
        cbUserMode.setCellFactory(f -> new UserModeListCell(false));
        cbUserMode.getSelectionModel().select(0);
        cbUserMode.setPadding(new Insets(0));
        cbUserMode.setStyle("-fx-background-color: transparent; -fx-text-fill: #fff; -fx-background-insets: 0; -fx-background-radius: 0");

        getUserModeManager().userModeProperty().bind(cbUserMode.getSelectionModel().selectedItemProperty());

        Menu menu = new Menu(null, cbUserMode);
        menu.setStyle("-fx-padding: 0");

        qupath.getMenuBar().getMenus().add(menu);
    }

    private void initializePreferences() {
        var sheet = QuPathGUI.getInstance().getPreferencePane().getPropertySheet();

        sheet.getItems().addAll(
            new PropertyItemBuilder<>(EduOptions.extensionEnabled(), Boolean.class)
                .name("QuPath Edu enabled")
                .description("Restart needed for changes to take effect")
                .category("QuPath Edu")
                .build(),

            new PropertyItemBuilder<>(EduOptions.host(), String.class)
                .name("QuPath Edu host")
                .description("Server used with QuPath Edu")
                .category("QuPath Edu")
                .build(),

            new PropertyItemBuilder<>(EduOptions.showLoginDialogOnStartup(), Boolean.class)
                .name("Show login dialog on startup")
                .description("If enabled, opens the login dialog on startup.")
                .category("QuPath Edu")
                .build()
        );

        // Hide annotation names in viewer by default for visual reasons
        qupath.getOverlayOptions().showNamesProperty().set(false);
    }

    public static void setWriteAccess(boolean hasWriteAccess) {
        noWriteAccess.set(!hasWriteAccess);
    }

    public static void setProjectInformation(String information) {
        projectInformation.setContent(information);
    }

    private void replaceViewer() {
        projectInformation.setOnMouseClicked(event -> {
            Project<BufferedImage> project = qupath.getProject();

            if (event.getClickCount() > 1 && project instanceof EduProject) {
                String projectId = ((EduProject) project).getId();

                if (!(qupath.isReadOnly()) && EduAPI.hasWritePermission(projectId)) {
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
        btnCreate.disableProperty().bind(qupath.readOnlyProperty());

        Button btnOpen = ActionTools.createButton(
            ActionTools.createAction(EduExtension::showWorkspaceOrLoginDialog, "Open lesson")
        );

        Button btnAdd = ActionTools.createButton(
            ActionTools.createAction(SlideManager::show, "Add images")
        );
        btnAdd.disableProperty().bind(qupath.readOnlyProperty().or(qupath.projectProperty().isNull()));

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
                LoginDialog.show();
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
