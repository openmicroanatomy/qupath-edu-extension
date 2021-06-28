package qupath.edu.gui;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.controlsfx.control.GridCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.gui.dialogs.WorkspaceManager;
import qupath.edu.util.ReflectionUtil;
import qupath.edu.api.EduAPI;
import qupath.edu.models.ExternalProject;
import qupath.lib.gui.dialogs.Dialogs;

import static qupath.edu.api.EduAPI.Result;

public class WorkspaceProjectListCell extends GridCell<ExternalProject> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final WorkspaceManager manager;
    private ExternalProject project;

    private boolean hasWriteAccess = false;

    public WorkspaceProjectListCell(WorkspaceManager manager) {
        this.manager = manager;

        setPrefWidth(0);
    }

    @Override
    protected void updateItem(ExternalProject project, boolean empty) {
        super.updateItem(project, empty);
        this.project = project;

        if (empty || project.getName().isEmpty()) {
            setText(null);
            setGraphic(null);
            return;
        }

        this.hasWriteAccess = EduAPI.isOwner(project.getOwner());

        GridPane pane = new GridPane();
        pane.setPadding(new Insets(5));
        pane.setHgap(5);
        pane.setPrefWidth(getGridView().getPrefWidth());

        if (project.isHidden()) {
            pane.setBorder(new Border(new BorderStroke(
                Color.DARKGRAY,           Color.DARKGRAY,           Color.DARKGRAY,           Color.DARKGRAY,
                BorderStrokeStyle.DASHED, BorderStrokeStyle.DASHED, BorderStrokeStyle.DASHED, BorderStrokeStyle.DASHED,
                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY
            )));

            pane.setBackground(new Background(new BackgroundFill(Color.LIGHTGRAY, null, null)));
        } else {
            pane.setBorder(new Border(new BorderStroke(
                null, null, Color.DARKGRAY,          null,
                null, null, BorderStrokeStyle.SOLID, null,
                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY
            )));
        }

        /* Constraints */

        ColumnConstraints logoColumnConstraint = new ColumnConstraints(48);
        ColumnConstraints textColumnConstraint = new ColumnConstraints();
        textColumnConstraint.setHgrow(Priority.ALWAYS);

        pane.getColumnConstraints().addAll(logoColumnConstraint, textColumnConstraint);

        RowConstraints headerRowConstraints = new RowConstraints(12, 24, 24);
        headerRowConstraints.setValignment(VPos.BOTTOM);

        RowConstraints descriptionRowConstraints = new RowConstraints(24, 24, 36);
        descriptionRowConstraints.setValignment(VPos.TOP);
        descriptionRowConstraints.setVgrow(Priority.ALWAYS);

        pane.getRowConstraints().addAll(headerRowConstraints, descriptionRowConstraints);

        /* Content */

        Text name = new Text(project.getName());
        name.setFont(Font.font(Font.getDefault().getName(), FontWeight.BOLD, 13));

        Label description = new Label(project.getDescription());
        description.setWrapText(true);

        ImageView icon = new ImageView(ReflectionUtil.loadIcon(48));

        /* Construct GridPane */

        pane.add(icon, 0, 0);
        pane.add(name, 1, 0);
        pane.add(description, 1, 1);

        GridPane.setRowSpan(icon, 2);

        pane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                WorkspaceManager.loadProject(project, manager);
            } else if (event.getButton() == MouseButton.SECONDARY) {
                ContextMenu menu = new ContextMenu();

                if (hasWriteAccess) {
                    MenuItem miRename = new MenuItem("Rename");
                    miRename.setOnAction(action -> renameProject());

                    MenuItem miEditDescription = new MenuItem("Edit description");
                    miEditDescription.setOnAction(action -> editDescription());

                    MenuItem miDelete = new MenuItem("Delete");
                    miDelete.setOnAction(action -> deleteProject());

                    MenuItem miToggleVisibility = new MenuItem(project.isHidden() ? "Reveal project" : "Hide project");
                    miToggleVisibility.setOnAction(action -> toggleVisibility());

                    menu.getItems().addAll(miRename, miEditDescription, miDelete, miToggleVisibility, new SeparatorMenuItem());
                }

                MenuItem miShare = new MenuItem("Share");
                miShare.setOnAction(action -> Dialogs.showInputDialog(
                    "Project ID",
                    "You can enter this ID to: Remote Slides > Open project by ID",
                    project.getId()
                ));

                menu.getItems().add(miShare);
                menu.show(pane, event.getScreenX(), event.getScreenY());
            }
        });

        setGraphic(pane);
        setPadding(new Insets(0));
    }

    private void editDescription() {
        String newDescription = Dialogs.showInputDialog(
            "New description", "", project.getDescription()
        );

        if (newDescription == null) {
            return;
        }

        Result result = EduAPI.editProject(project.getId(), project.getName(), newDescription);

        if (result == Result.OK) {
            manager.refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully edited project.");
        } else {
            Dialogs.showErrorNotification(
                "Error when editing project description",
                "See log for more details"
            );
        }
    }

    private void renameProject() {
        String newName = Dialogs.showInputDialog(
            "New name", "", project.getName()
        );

        if (newName == null) {
            return;
        }

        Result result = EduAPI.editProject(project.getId(), newName, project.getDescription());

        if (result == Result.OK) {
            manager.refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully renamed project.");
        } else {
            Dialogs.showErrorNotification(
                "Error when editing project name",
                "See log for more details"
            );
        }
    }

    private void deleteProject() {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to delete this project? This action is irreversible."
        );

        if (confirm) {
            Result result = EduAPI.deleteProject(project.getId());

            if (result == Result.OK) {
                manager.refreshDialog();
                Dialogs.showInfoNotification("Success", "Successfully deleted project.");
            } else {
                Dialogs.showErrorNotification(
                    "Error when deleting project",
                    "See log for more details"
                );
            }
        }
    }

    private void toggleVisibility() {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to set this project " + (project.isHidden() ? "visible" : "hidden")
        );

        if (confirm) {
            Result result = EduAPI.setProjectHidden(project.getId(), !project.isHidden());

            if (result == Result.OK) {
                manager.refreshDialog();
                Dialogs.showInfoNotification("Success", "Successfully toggled visibility.");
            } else {
                Dialogs.showErrorNotification(
                    "Error while setting project visibility",
                    "See log for more details"
                );
            }
        }
    }
}
