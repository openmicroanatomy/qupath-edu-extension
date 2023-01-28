package qupath.edu.gui.dialogs;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.gui.buttons.IconButtons;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.edu.models.ExternalOrganization;
import qupath.edu.models.ExternalUser;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RemoteUserManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showManagementDialog() {
        RemoteUserManager manager = new RemoteUserManager();

        dialog = Dialogs.builder()
                .title("User management")
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

    private synchronized void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
            return;
        }

        initializePane();
        dialog.getDialogPane().setContent(pane);
    }

    private synchronized void initializePane() {
        /* Table */

        TableView<ExternalUser> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Text("No users, none match search criteria or no permissions."));

        TableColumn<ExternalUser, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setReorderable(false);

        TableColumn<ExternalUser, String> emailColumn = new TableColumn<>("Email");
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailColumn.setReorderable(false);

        TableColumn<ExternalUser, String> organizationColumn = new TableColumn<>("Organization");
        organizationColumn.setCellValueFactory(new PropertyValueFactory<>("organizationName"));
        organizationColumn.setReorderable(false);

        table.getColumns().addAll(nameColumn, emailColumn, organizationColumn);

        /* Filter / Search */

        TextField tfFilter = new TextField();
        tfFilter.setPromptText("Search by name, organization or ID");

        FilteredList<ExternalUser> filteredData = new FilteredList<>(FXCollections.observableArrayList(EduAPI.getAllUsers()), data -> true);

        tfFilter.textProperty().addListener(((observable, oldValue, newValue) -> {
            filteredData.setPredicate(data -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseSearch = newValue.toLowerCase();
                return data.toString().replaceAll("[-+.^:,']", "").toLowerCase().contains(lowerCaseSearch);
            });
        }));

        SortedList<ExternalUser> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());

        table.setItems(sortedData);

        /* Properties */

        var selected = table.getSelectionModel().selectedItemProperty();
        var hasPermission = new SimpleBooleanProperty(EduAPI.hasRole(Roles.MANAGE_USERS));

        /* Table onClick */

        table.setOnMouseClicked(event -> {
            if (event.getClickCount() > 1 && selected.get() != null && hasPermission.get()) {
                editUser(selected.get());
            }
        });

        /* Buttons */

        Button btnCreate = new Button("Create a new user");
        btnCreate.setOnAction(a -> createUser());
        btnCreate.disableProperty().bind(hasPermission.not());

        Button btnEdit = new Button("Edit selected");
        btnEdit.setOnAction(a -> editUser(selected.get()));
        btnEdit.disableProperty().bind(selected.isNull().or(hasPermission.not()));

        Button btnDelete = new Button("Delete selected");
        btnDelete.setOnAction(a -> deleteUser(selected.get()));
        btnDelete.disableProperty().bind(selected.isNull().or(hasPermission.not()));

        GridPane buttons = PaneTools.createColumnGridControls(btnCreate, btnEdit, btnDelete);
        buttons.setHgap(5);

        /* Pane */

        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        pane = new BorderPane();
        pane.setPrefWidth(600);
        pane.setPrefHeight(400);
        pane.setTop(tfFilter);
        pane.setCenter(table);
        pane.setBottom(buttons);
        pane.setPadding(new Insets(10));
    }

    private void editUser(ExternalUser user) {
        /* GridPane */

        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.getColumnConstraints().add(new ColumnConstraints(300));

        /* Name */

        TextField tfName = new TextField();
        tfName.setText(user.getName());
        tfName.setDisable(true);

        Button btnEditName = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
        btnEditName.setOnAction(a -> tfName.setDisable(false));

        /* Email */

        TextField tfEmail = new TextField();
        tfEmail.setText(user.getEmail());
        tfEmail.setDisable(true);

        Button btnEditEmail = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
        btnEditEmail.setOnAction(a -> tfEmail.setDisable(false));

        /* Password */

        PasswordField tfPassword = new PasswordField();
        tfPassword.setText("**************");
        tfPassword.setDisable(true);

        Button btnEditPassword = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
        btnEditPassword.setOnAction(a -> tfPassword.setDisable(false));
        btnEditPassword.setDisable(!EduAPI.hasRole(Roles.ADMIN));

        /* Organization */

        ComboBox<ExternalOrganization> cbOrganization = new ComboBox<>();
        cbOrganization.setDisable(true);
        cbOrganization.setMaxWidth(Double.MAX_VALUE);
        cbOrganization.setCellFactory(p -> new OrganizationNameListCell());
        cbOrganization.setButtonCell(new OrganizationNameListCell());

        Button btnEditOrganization;

        if (EduAPI.hasRole(Roles.ADMIN)) {
            btnEditOrganization = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
            btnEditOrganization.setOnAction(a -> cbOrganization.setDisable(false));

            cbOrganization.setItems(FXCollections.observableArrayList(EduAPI.getAllOrganizations().orElse(List.of())));
            cbOrganization.getSelectionModel().select(user.getOrganization());
        } else {
            btnEditOrganization = IconButtons.createIconButton(FontAwesome.Glyph.QUESTION_CIRCLE);
            btnEditOrganization.setOnAction(a -> Dialogs.showMessageDialog(
                "Changing user organization",
                "Please contact an administrator if you wish to migrate an user to a different organization." +
                "\n\n" +
                "Only users who authenticate via credentials can have their organization changed."
            ));

            cbOrganization.setItems(FXCollections.observableArrayList(user.getOrganization()));
        }

        /* Pane */

        int row = 0;

        pane.add(tfName, 0, ++row);
        pane.add(btnEditName, 1, row);

        pane.add(tfEmail, 0, ++row);
        pane.add(btnEditEmail, 1, row);

        pane.add(tfPassword, 0, ++row);
        pane.add(btnEditPassword, 1, row);

        pane.add(cbOrganization, 0, ++row);
        pane.add(btnEditOrganization, 1, row);

        /* Separator */

        Separator separator = new Separator(Orientation.HORIZONTAL);
        pane.add(separator, 0, ++row);
        GridPane.setColumnSpan(separator, 2);

        /* Roles */

        Map<Roles, CheckBox> checkboxes = new HashMap<>();
        for (Roles role : Roles.getModifiableRoles()) {
            CheckBox checkbox = new CheckBox();
            checkbox.setSelected(user.getRoles().contains(role));

            Label label = new Label(role.getName());
            label.setFont(Font.font(null, FontWeight.BOLD, -1));
            label.setLabelFor(checkbox);
            label.setAlignment(Pos.BASELINE_RIGHT);

            Label details = new Label(role.getDescription());
            details.setFont(Font.font(null, FontWeight.NORMAL, 10));

            // Prohibit removing MANAGE_USERS permission from themselves
            if (role.equals(Roles.MANAGE_USERS) && user.getId().equals(EduAPI.getUserId())) {
                checkbox.setDisable(true);
                label.setTooltip(new Tooltip("Cannot modify this permission."));
            }

            pane.add(label, 0, ++row);
            pane.add(checkbox, 1, row);
            pane.add(details, 0, ++row);

            checkboxes.put(role, checkbox);
        }

        GridPane.setHgrow(tfName, Priority.ALWAYS);

        /* Edit Dialog */

        Optional<ButtonType> result = Dialogs.builder()
                .title("Edit user")
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .content(pane)
                .showAndWait();

        if (result.isPresent() && result.get().equals(ButtonType.OK)) {
            var confirm = Dialogs.showYesNoDialog(
                "Save changes",
                "Save any changes made to user?"
            );

            if (!confirm) {
                return;
            }

            Map<String, Object> formData = new HashMap<>();
            for (Map.Entry<Roles, CheckBox> entry : checkboxes.entrySet()) {
                formData.put(entry.getKey().name(), entry.getValue().isSelected());
            }

            formData.put("name", tfName.getText());
            formData.put("email", tfEmail.getText());

            if (!tfPassword.isDisabled()) {
                formData.put("password", tfPassword.getText());
            }

            if (!cbOrganization.isDisabled()) {
                // TODO: If user updates their own organization it would not reflect on EduAPI
                formData.put("organization", cbOrganization.getValue().getId());
            }

            if (EduAPI.editUser(user.getId(), formData)) {
                refresh();
                Dialogs.showInfoNotification("Success", "Successfully edited user.");
            } else {
                Dialogs.showErrorNotification("Error", "Error while editing user. See log for possibly more details.");
            }
        }
    }

    private void deleteUser(ExternalUser user) {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to delete this user? This action is irreversible."
        );

        if (!confirm) {
            return;
        }

        boolean success = EduAPI.deleteUser(user.getId());

        if (success) {
            refresh();
            Dialogs.showInfoNotification("Success", "Successfully deleted user.");
        } else {
            Dialogs.showErrorNotification("Error", "Error while deleting user. See log for possibly more details.");
        }
    }

    private void createUser() {
        /* Fields */

        TextField tfName = new TextField();
        tfName.setPromptText("Name");

        TextField tfEmail = new TextField();
        tfEmail.setPromptText("Email");

        TextField tfPassword = new PasswordField();
        tfPassword.setPromptText("Password");

        TextField tfPasswordRepeat = new PasswordField();
        tfPasswordRepeat.setPromptText("Repeat password");

        ComboBox<ExternalOrganization> cbOrganization = new ComboBox<>();
        cbOrganization.setMaxWidth(Double.MAX_VALUE);
        cbOrganization.setCellFactory(p -> new OrganizationNameListCell());
        cbOrganization.setButtonCell(new OrganizationNameListCell());

        if (EduAPI.hasRole(Roles.ADMIN)) {
            cbOrganization.setPromptText("Select organization");
            cbOrganization.setItems(FXCollections.observableArrayList(EduAPI.getAllOrganizations().orElse(List.of())));
            cbOrganization.setPlaceholder(new Text("No organizations"));
        } else {
            cbOrganization.setItems(FXCollections.observableArrayList(EduAPI.getOrganization().orElse(null)));
            cbOrganization.setDisable(true);
        }

        /* Pane */

        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.setPrefWidth(300);

        int row = 0;

        pane.add(tfName, 0, ++row);
        pane.add(tfEmail, 0, ++row);
        pane.add(tfPassword, 0, ++row);
        pane.add(tfPasswordRepeat, 0, ++row);
        pane.add(cbOrganization, 0, ++row);

        GridPane.setHgrow(tfName, Priority.ALWAYS);
        GridPane.setHgrow(tfEmail, Priority.ALWAYS);
        GridPane.setHgrow(tfPassword, Priority.ALWAYS);
        GridPane.setHgrow(tfPasswordRepeat, Priority.ALWAYS);
        GridPane.setHgrow(cbOrganization, Priority.ALWAYS);

        /* Dialog */

        Optional<ButtonType> result = Dialogs.builder()
                .title("Edit user")
                .buttons(ButtonType.FINISH, ButtonType.CANCEL)
                .content(pane)
                .showAndWait();

        if (result.isPresent() && result.get().equals(ButtonType.FINISH)) {
            if (!tfPassword.getText().equals(tfPasswordRepeat.getText())) {
                // TODO: Keep dialog open, just empty the password fields.
                Dialogs.showErrorNotification("Error", "Passwords do not match");
                return;
            }

            if (cbOrganization.getValue() == null) {
                Dialogs.showErrorNotification("Error", "Please select an organization");
                return;
            }

            Optional<ExternalUser> user = EduAPI.createUser(
                tfPassword.getText(), tfEmail.getText(), tfName.getText(), cbOrganization.getValue().getId()
            );

            if (user.isPresent()) {
                refresh();
                Dialogs.showInfoNotification("Success", "Successfully created user.");
            } else {
                Dialogs.showErrorNotification("Error", "Error while creating user. See log for possibly more details.");
            }
        }
    }

    static class OrganizationNameListCell extends ListCell<ExternalOrganization> {
        @Override protected void updateItem(ExternalOrganization organization, boolean empty) {
            super.updateItem(organization, empty);

            if (organization == null || empty) {
                setGraphic(null);
                setText(null);
            } else {
                setText(organization.getName());
            }
        }
    }
}
