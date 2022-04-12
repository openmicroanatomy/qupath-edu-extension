package qupath.edu.gui.dialogs;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.controlsfx.control.ListSelectionView;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.edu.models.ExternalOrganization;
import qupath.edu.models.ExternalOwner;
import qupath.edu.models.ExternalWorkspace;
import qupath.lib.gui.dialogs.Dialogs;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorkspacePermissionManager {

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showDialog() {
        WorkspacePermissionManager manager = new WorkspacePermissionManager();

        dialog = Dialogs.builder()
                .title("Workspace permission management")
                .content(manager.getPane())
                .buttons(ButtonType.CLOSE)
                .build();

        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
    }

    public synchronized BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        MasterDetailPane mdPane = new MasterDetailPane();

        // Only list workspaces which belong to the users current organization.
        // Admins can edit all workspaces regardless of the organization.
        List<ExternalWorkspace> workspaces = EduAPI.getAllWorkspaces()
                .stream()
                .filter(workspace -> EduAPI.hasRole(Roles.ADMIN) || workspace.getOwnerId().equals(EduAPI.getOrganizationId()))
                .collect(Collectors.toList());

        ListView<ExternalWorkspace> lvWorkspaces = new ListView<>();
        lvWorkspaces.setCellFactory(param -> new WorkspaceNameListCell());
        lvWorkspaces.setItems(FXCollections.observableArrayList(workspaces));
        lvWorkspaces.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        lvWorkspaces.setPlaceholder(new Label("No workspaces available"));
        lvWorkspaces.getSelectionModel().selectedItemProperty().addListener((o, old, workspace) -> {
            createReadWriteTabs(mdPane, workspace.getId());
        });

        mdPane.setPrefWidth(1400);
        mdPane.setPrefHeight(600);
        mdPane.setMasterNode(lvWorkspaces);
        mdPane.setDetailSide(Side.RIGHT);

        // Bug with ControlsFX: setDividerPosition(); requires the pane to be rendered first.
        Platform.runLater(() -> {
            mdPane.setDetailNode(new Placeholder("Select a workspace to continue"));
            mdPane.setDividerPosition(0.2);
        });

        pane = new BorderPane();
        pane.setCenter(mdPane);
    }

    private void createReadWriteTabs(MasterDetailPane mdPane, String workspaceId) {
        if (EduAPI.hasWritePermission(workspaceId)) {
            // Fetch the latest permissions for this workspace, as they might have changed since the dialog
            // was opened initially. This also solves a bug where editing permissions and switching between
            // workspaces would reset the permissions to the state they were when initially opening the dialog.
            ExternalWorkspace workspace = EduAPI.getWorkspace(workspaceId).get();

            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getTabs().add(new Tab("Write", createListSelectionView(workspace, true)));
            tabs.getTabs().add(new Tab("Read",  createListSelectionView(workspace, false)));

            mdPane.setDetailNode(tabs);
        } else {
            mdPane.setDetailNode(new Placeholder("Not authorized to edit permissions for this workspace"));
        }
    }

    /**
     * Create a ListSelectionView to modify workspace permissions.
     * @param workspace workspace being edited
     * @param write if ListSelectionView is for write permissions, false for read permissions
     * @return ListSelectionView
     */
    private ListSelectionView<ExternalOwner> createListSelectionView(ExternalWorkspace workspace, boolean write) {
        // TODO: Cache result and re-use it
        // Fetch all organizations and users into a single List
        List<ExternalOwner> available = Stream.concat(
            EduAPI.getAllOrganizations().orElse(List.of()).stream(),
            EduAPI.getAllUsers().stream()
        ).collect(Collectors.toList());

        // Get entities with read/write permission currently
        List<ExternalOwner> current = write ? workspace.getEntitiesWithWritePermission() : workspace.getEntitiesWithReadPermission();

        // Remove entities from the "Available" list who already have read/write permission for that workspace.
        available.removeAll(current);

        ListSelectionView<ExternalOwner> listSelectionView = new ListSelectionView<>();
        listSelectionView.getActions().add(new ExplainPermissionsAction(write));
        listSelectionView.setCellFactory(c -> new ExternalOwnerNameListCell());
        listSelectionView.setSourceItems(FXCollections.observableArrayList(available));
        listSelectionView.setTargetItems(FXCollections.observableArrayList(current));

        // Check if user is about to remove write permissions from themselves.
        listSelectionView.getTargetItems().addListener((ListChangeListener<ExternalOwner>) change -> {
            while (change.next() && change.wasRemoved()) {
                boolean match = change
                        .getRemoved()
                        .stream()
                        .anyMatch(owner -> owner.getId().equals(EduAPI.getUserId()));

                if (match && !(EduAPI.getRoles().contains(Roles.ADMIN))) {
                    Dialogs.showMessageDialog(
                        "Warning",
                        "You're about to remove permissions from yourself. " +
                        "\n\n" +
                        "If you save these changes, you will not be able to edit this workspace anymore unless someone restores your write permissions."
                    );
                }
            }
        });

        Button btnSave = new Button("Save changes");
        btnSave.setOnAction(a -> save(workspace, listSelectionView.getTargetItems(), write));

        HBox buttons = new HBox(btnSave);
        buttons.setAlignment(Pos.BASELINE_RIGHT);
        buttons.setSpacing(10);

        listSelectionView.setTargetFooter(buttons);

        return listSelectionView;
    }

    private void save(ExternalWorkspace workspace, ObservableList<ExternalOwner> owners, boolean write) {
        EduAPI.Result result;

        if (write) {
            result = EduAPI.editWorkspaceWritePermissions(workspace.getId(), owners);
        } else {
            result = EduAPI.editWorkspaceReadPermissions(workspace.getId(), owners);
        }

        if (result == EduAPI.Result.OK) {
            Dialogs.showInfoNotification("Success", "Successfully edited permissions.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when editing permissions. Check log for possibly more information.");
        }
    }

    static class Placeholder extends Label {

        public Placeholder(String text) {
            super(text);
            setAlignment(Pos.CENTER);
        }
    }

    static class WorkspaceNameListCell extends ListCell<ExternalWorkspace> {

        @Override
        protected void updateItem(ExternalWorkspace workspace, boolean empty) {
            super.updateItem(workspace, empty);

            if (workspace == null || empty) {
                setGraphic(null);
                setText(null);
            } else {
                setText(workspace.getName());
            }
        }
    }

    static class ExternalOwnerNameListCell extends ListCell<ExternalOwner> {

        private final GlyphFont awesome = GlyphFontRegistry.font("FontAwesome");

        @Override
        protected void updateItem(ExternalOwner owner, boolean empty) {
            super.updateItem(owner, empty);

            if (owner == null || empty) {
                setGraphic(null);
                setText(null);
            } else {
                if (owner instanceof ExternalOrganization) {
                    setGraphic(awesome.create(FontAwesome.Glyph.BUILDING).size(18));
                } else {
                    setGraphic(awesome.create(FontAwesome.Glyph.USER).size(18));
                }

                setText(owner.getName());
            }
        }
    }

    static class ExplainPermissionsAction extends ListSelectionView.ListSelectionAction<ExternalOwner> {

        private final boolean write;

        public ExplainPermissionsAction(boolean write) {
            super(GlyphFontRegistry.font("FontAwesome").create(FontAwesome.Glyph.QUESTION));
            this.write = write;
        }

        @Override
        public void initialize(ListView<ExternalOwner> available, ListView<ExternalOwner> selected) {
            if (write) {
                selected.setPlaceholder(new Label("Administrators"));
            } else {
                selected.setPlaceholder(new Label("Everyone"));
            }

            setEventHandler(ae -> {
                StringBuilder message = new StringBuilder();

                if (write) {
                    message.append("Who are authorized to make changes to this workspace");
                } else {
                    message.append("Who are authorized to view this workspace");
                }

                message.append("\n\n");

                if (selected.getItems().isEmpty()) {
                    if (write) {
                        message.append("Administrators");
                    } else {
                        message.append("Everyone");
                    }
                }

                for (ExternalOwner owner : selected.getItems()) {
                    // TODO: Make GSON serialize objects properly to ExternalOrganization and ExternalUser
//                    if (owner instanceof ExternalOrganization) {
//                        message.append("All the users who belong to the organization");
//                        message.append(" ");
//                        message.append(owner.getName());
//                    } else {
//                        message.append("User");
//                        message.append(" ");
//                        message.append(owner.getName());
//                    }

                    message.append(owner.getName());
                    message.append("\n");
                }

                Dialogs.showMessageDialog("Permission analysis", message.toString());
            });
        }
    }
}
