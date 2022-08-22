package qupath.edu.gui.dialogs;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.controlsfx.control.GridView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.EduOptions;
import qupath.edu.gui.SubjectListCell;
import qupath.edu.gui.WorkspaceProjectListCell;
import qupath.edu.util.ReflectionUtil;
import qupath.edu.api.EduAPI;
import qupath.edu.EduProject;
import qupath.edu.models.ExternalProject;
import qupath.edu.models.ExternalSlide;
import qupath.edu.models.ExternalSubject;
import qupath.edu.models.ExternalWorkspace;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static qupath.edu.api.EduAPI.Result;

public class WorkspaceManager {

    private final static Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

    private final SimpleBooleanProperty hasAccessProperty = new SimpleBooleanProperty(false);
    private final SimpleObjectProperty<ExternalWorkspace> currentWorkspace  = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<ExternalSubject>   currentSubject    = new SimpleObjectProperty<>();

    private static Dialog<ButtonType> dialog;
    private final QuPathGUI qupath;

    private BorderPane pane;
    private SplitPane splitPane;

    private final Accordion accordion = new Accordion();
    private final ObservableList<ExternalProject> currentProjects = FXCollections.observableArrayList();

    public static void showWorkspace(QuPathGUI qupath) {
        WorkspaceManager manager = new WorkspaceManager(qupath);

        dialog = Dialogs.builder()
                .title("Select lesson")
                .content(manager.getPane())
                .size(1000, 600)
                .build();

        dialog.getDialogPane().getStylesheets().add(RemoteServerLoginManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
        dialog.setResult(ButtonType.CLOSE);
        dialog.show();

        manager.getSplitPane().setDividerPositions(0.2);

        // Disable SplitPane divider
        manager.getSplitPane().lookupAll(".split-pane-divider").stream().forEach(div -> div.setMouseTransparent(true));
    }

    private WorkspaceManager(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    public Accordion getAccordion() {
        return accordion;
    }

    public SplitPane getSplitPane() {
        return splitPane;
    }

    private synchronized void initializePane() {
        pane = new BorderPane();

        // TODO: Make this async or make a loading dialog

        List<ExternalWorkspace> workspaces = EduAPI.getAllWorkspaces();

        /* Header Buttons */

        Button btnLogout = new Button("Logout / change organization");
        btnLogout.setFont(new Font(10));
        btnLogout.setOnAction(a -> logout());

        MenuItem miOpenById = new MenuItem("Lesson or slide");
        miOpenById.setOnAction(this::openById);

        MenuItem miOpenLocalProject = new MenuItem("QuPath project");
        miOpenLocalProject.setOnAction(qupath.lookupActionByText("Open project"));

        MenuButton btnOpen = new MenuButton("Open ...");
        btnOpen.getItems().addAll(miOpenById, miOpenLocalProject);
        btnOpen.setFont(Font.font(10));

        /* Header */

        GridPane header = new GridPane();
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setPercentWidth(50);

        header.getColumnConstraints().addAll(constraint, constraint);
        header.addRow(0, btnOpen, btnLogout);

        GridPane.setHalignment(btnOpen, HPos.LEFT);
        GridPane.setHalignment(btnLogout, HPos.RIGHT);

        /* Footer Buttons */

        // TODO: Fix disabled buttons when no workspaces available
        //       Fix not taking MANAGE_PROJECTS into account

        MenuItem miCreateWorkspace = new MenuItem("Workspace");
        miCreateWorkspace.setOnAction(action -> createNewWorkspace());
        miCreateWorkspace.disableProperty().bind(hasAccessProperty.not().and(currentWorkspace.isNotNull()));

        MenuItem miCreateSubject = new MenuItem("Course");
        miCreateSubject.setOnAction(action -> createNewCourse());
        miCreateSubject.disableProperty().bind(hasAccessProperty.not().or(currentWorkspace.isNull()));

        MenuItem miCreateProject = new MenuItem("Lesson");
        miCreateProject.setOnAction(action -> createNewLesson());
        miCreateProject.disableProperty().bind(hasAccessProperty.not().or(currentSubject.isNull()));

        MenuItem miCreateLocalProject = new MenuItem("QuPath project");
        miCreateLocalProject.setOnAction(qupath.lookupActionByText("Create project"));

        MenuButton menuCreate = new MenuButton("Create ...");
        menuCreate.getItems().addAll(miCreateWorkspace, miCreateSubject, miCreateProject, new SeparatorMenuItem(), miCreateLocalProject);

        Button btnClose = new Button("Close");
        btnClose.setOnAction(action -> closeDialog());

        /* Footer */

        ButtonBar.setButtonData(menuCreate, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(btnClose, ButtonBar.ButtonData.RIGHT);
        ButtonBar.setButtonUniformSize(btnClose, false);

        ButtonBar footer = new ButtonBar();
        footer.getButtons().addAll(menuCreate, btnClose);

        /* Content */

        GridView<ExternalProject> gvProjects = new GridView<>();
        gvProjects.setCellFactory(f -> new WorkspaceProjectListCell(this));
        gvProjects.setPadding(new Insets(0));
        gvProjects.setHorizontalCellSpacing(10);
        gvProjects.setCellWidth(360); // TODO: Bind to parent width
        gvProjects.setItems(currentProjects);

        // GridView adds a fixed 18px for the scrollbar and each cell has padding of 10px left and right.
        gvProjects.cellWidthProperty().bind(gvProjects.widthProperty().subtract(18).divide(2).subtract(20));

        splitPane = new SplitPane();
        splitPane.setPrefWidth(1000);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(accordion, gvProjects);

        createAccordion(accordion, workspaces);

        accordion.expandedPaneProperty().addListener(onWorkspaceChange());

        selectPreviousWorkspace(accordion);

        pane.setPadding(new Insets(0));
        pane.setPrefHeight(600);
        pane.setTop(header);
        pane.setCenter(splitPane);
        pane.setBottom(footer);

        BorderPane.setMargin(footer, new Insets(10));
        BorderPane.setMargin(header, new Insets(10));
    }

    private void createAccordion(Accordion accordion, List<ExternalWorkspace> workspaces) {
        accordion.getPanes().clear();
        currentProjects.clear();

        for (ExternalWorkspace workspace : workspaces) {
            if (!belongsToSameOrganizationAsUser(workspace)) {
                continue;
            }

            var hasWriteAccess = EduAPI.hasWritePermission(workspace.getId());

            ListView<ExternalSubject> lvSubjects = new ListView<>();
            lvSubjects.setCellFactory(f -> new SubjectListCell(this, hasWriteAccess));
            lvSubjects.getItems().addAll(workspace.getSubjects());
            lvSubjects.setOnMouseClicked(e -> {
                ExternalSubject selectedSubject = lvSubjects.getSelectionModel().getSelectedItem();

                if (selectedSubject == null) {
                    return;
                }

                if (e.getButton() == MouseButton.PRIMARY) {
                    currentSubject.set(selectedSubject);
                    currentProjects.setAll(selectedSubject.getProjects());
                }
            });

            TitledPane tpWorkspace = new TitledPane(workspace.getName(), lvSubjects);
            tpWorkspace.setUserData(workspace);

            if (hasWriteAccess) {
                MenuItem miRename = new MenuItem("Rename workspace");
                miRename.setOnAction(a -> renameWorkspace(workspace));

                MenuItem miDelete = new MenuItem("Delete workspace");
                miDelete.setOnAction(e -> deleteWorkspace(workspace));

                // setContextMenu(...) would show up on everywhere on the TitlePane,
                // setOnMouseClicked(...) only registers when clicking on the header i.e. workspace name
                tpWorkspace.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.SECONDARY) {
                        ContextMenu menu = new ContextMenu(miRename, miDelete);
                        menu.show(tpWorkspace, e.getScreenX(), e.getScreenY());
                    }
                });
            }

            accordion.getPanes().add(tpWorkspace);
        }
    }

    /**
     * Check if the given workspace belongs to the same organization as the user.
     */
    private boolean belongsToSameOrganizationAsUser(ExternalWorkspace workspace) {
        return workspace.getOwnerId().equals(EduAPI.getOrganizationId()) || workspace.getOwnerId().equals(EduAPI.getUserId());
    }

    private void openById(ActionEvent actionEvent) {
        String id = Dialogs.showInputDialog(
            "Lesson or slide ID",
            "All IDs are similar to 6ce7a026-e023-47b5-9b2e-0fc5eb523e49",
            ""
        );

        if (id == null) {
            return;
        }

        List<ExternalSlide> slides = EduAPI.getAllSlides();
        Optional<ExternalSlide> slide = slides.stream().filter(s -> s.getId().equalsIgnoreCase(id.strip())).findFirst();
        if (slide.isPresent()) {
            ExternalSlideManager.openSlide(slide.get());
        } else {
            ExternalProject project = new ExternalProject();
            project.setId(id.strip());
            project.setName(id.strip());

            WorkspaceManager.loadProject(project);
        }

        closeDialog();
    }

    private void selectPreviousWorkspace(Accordion accordion) {
        if (EduOptions.previousWorkspace().get() != null) {
            accordion.getPanes().forEach(workspace -> {
                String id = ((ExternalWorkspace) workspace.getUserData()).getId();

                if (id != null && id.equals(EduOptions.previousWorkspace().get())) {
                    accordion.setExpandedPane(workspace);
                }
            });
        }
    }

    private ChangeListener<TitledPane> onWorkspaceChange() {
        return (obs, oldWorkspace, newWorkspace) -> {
            if (newWorkspace == null) {
                return;
            }

            ExternalWorkspace workspace = (ExternalWorkspace) newWorkspace.getUserData();

            if (workspace.getId() != null) {
                currentWorkspace.set(workspace);

                var hasWritePermission = EduAPI.hasWritePermission(workspace.getId());
                hasAccessProperty.set(hasWritePermission);
                EduExtension.setWriteAccess(hasWritePermission);

                // Open the first course from this workspace.
                if (workspace.getSubjects().size() > 0) {
                    ExternalSubject subject = workspace.getSubjects().get(0);
                    currentSubject.set(subject);
                    currentProjects.setAll(subject.getProjects());
                }
            }
        };
    }

    public void refreshDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshDialog);
            return;
        }

        ExternalWorkspace previousWorkspace;

        if (accordion.getExpandedPane() == null) {
            previousWorkspace = null;
        } else {
            previousWorkspace = ((ExternalWorkspace) accordion.getExpandedPane().getUserData());
        }

        List<ExternalWorkspace> workspaces = EduAPI.getAllWorkspaces();
        createAccordion(accordion, workspaces);

        // Restore the previously open TitlePane

        if (previousWorkspace == null) {
            return;
        }

        accordion.getPanes().stream()
                .filter(pane -> previousWorkspace.getId().equals(((ExternalWorkspace) pane.getUserData()).getId()))
                .findFirst()
                .ifPresent(accordion::setExpandedPane);

        workspaces.stream()
                .filter(workspace -> workspace.getId().equals(previousWorkspace.getId()))
                .findFirst()
                .flatMap(workspace -> workspace.findSubject(currentSubject.get()))
                .ifPresent(subject -> currentProjects.setAll(subject.getProjects()));
    }

    private void createNewLesson() {
        String name = Dialogs.showInputDialog("Lesson name", "", "");

        if (name == null) {
            return;
        }

        Result result = EduAPI.createProject(currentSubject.get().getId(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully created lesson.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when creating lesson. See log for possibly more details.");
        }
    }

    public void createNewCourse() {
        String name = Dialogs.showInputDialog("Course name", "", "");

        if (name == null) {
            return;
        }

        Result result = EduAPI.createSubject(currentWorkspace.get(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully created course.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when creating course. See log for possibly more details.");
        }
    }

    private void createNewWorkspace() {
        String name = Dialogs.showInputDialog("Workspace name", "", "");

        if (name == null) {
            return;
        }

        Result result = EduAPI.createWorkspace(name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully created workspace");
        } else {
            Dialogs.showErrorNotification("Error", "Error when creating workspace. See log for possibly more details.");
        }
    }

    private void renameWorkspace(ExternalWorkspace workspace) {
        String name = Dialogs.showInputDialog("Workspace name", "", workspace.getName());

        if (name == null) {
            return;
        }

        Result result = EduAPI.renameWorkspace(workspace.getId(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully renamed workspace.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when renaming workspace. See log for possibly more details.");
        }

    }

    private void deleteWorkspace(ExternalWorkspace workspace) {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to delete this workspace?" +
            "\n\n" +
            "This will also delete all the courses and lessons that belong to this workspace. This action is irreversible."
        );

        if (!confirm) {
            return;
        }

        Result result = EduAPI.deleteWorkspace(workspace.getId());

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully deleted workspace.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when deleting workspace. See log for possibly more details.");
        }
    }

    public void renameSubject(ExternalSubject subject) {
        String name = Dialogs.showInputDialog("Course name", "", subject.getName());

        if (name == null) {
            return;
        }

        Result result = EduAPI.renameSubject(subject.getId(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully course course.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when renaming course. See log for possibly more details.");
        }
    }

    public void deleteSubject(ExternalSubject subject) {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to delete this course?" +
            "\n\n" +
            "This will also delete all the projects belonging to this course. This action is irreversible."
        );

        if (!confirm) {
            return;
        }

        Result result = EduAPI.deleteSubject(subject.getId());

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully deleted course.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when deleting course. See log for possibly more details.");
        }
    }

    private void logout() {
        // Confirm logging out if logged in

        if (!(EduAPI.getAuthType().shouldPrompt()) || Dialogs.showConfirmDialog("Are you sure?", "Are you sure you wish to log out?")) {
            // TODO: Multiple viewers
            qupath.getViewer().setImageData(null);
            qupath.setProject(null);
            EduAPI.logout();
            closeDialog();

            EduExtension.showWorkspaceOrLoginDialog();
        }
    }

    public void closeDialog() {
        dialog.close();
    }

    /**
     * Loads an external project based on ExternalProject. If manager is defined, it tries to close the workspace
     * manager dialog, but this method can be used without it to load projects manually.
     * @param extProject External project to load, requires at least UUID and name defined.
     * @param manager WorkspaceManager to close, can be null.
     */
    public static void loadProject(ExternalProject extProject, WorkspaceManager manager) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            // Confirm that the ID is a valid UUID
            UUID.fromString(extProject.getId());
        } catch (IllegalArgumentException e) {
            Dialogs.showErrorNotification("Error", "Provided ID was formatted incorrectly.");
            EduExtension.showWorkspaceOrLoginDialog();
            return;
        }

        try {
            Task<Optional<String>> worker = new Task<>() {
                @Override
                protected Optional<String> call() {
                    updateMessage("Downloading lesson");
                    Optional<String> projectData = EduAPI.downloadProject(extProject.getIdWithTimestamp());

                    if (projectData.isEmpty()) {
                        Dialogs.showErrorNotification("Error", "Error when downloading lesson, see log for possibly more details.");
                        return Optional.empty();
                    }

                    updateMessage("Downloaded. Opening lesson...");

                    return projectData;
                }
            };

            ProgressDialog progress = new ProgressDialog(worker);
            progress.setTitle("Lesson import");
            qupath.submitShortTask(worker);
            progress.showAndWait();

            var projectData = worker.getValue();

            if (projectData.isPresent()) {
                EduProject project = new EduProject(projectData.get());
                project.setId(extProject.getId());
                project.setName(extProject.getName());

                if (manager != null) {
                    manager.closeDialog();
                }

                Platform.runLater(() -> {
                    ReflectionUtil.getAnalysisPanel().getSelectionModel().select(0);
                    qupath.setProject(project);
                });
            }
        } catch (IOException e) {
            Dialogs.showErrorMessage(
                "Error when trying to load lesson. ",
                "See log for more information."
            );

            logger.error("Error when loading external lesson", e);
        }
    }

    public static void loadProject(String id, String name) {
        ExternalProject project = new ExternalProject();
        project.setId(id);
        project.setName(name);

        loadProject(project);
    }

    public static void loadProject(ExternalProject project) {
        loadProject(project, null);
    }
}
