package qupath.edu.gui.dialogs;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import qupath.edu.api.EduAPI;
import qupath.lib.gui.dialogs.Dialogs;

public class PasswordResetGUI {

    public void showRequestPasswordResetTokenDialog() {
        /* Textfields */

        Label labEmail = new Label("Email");
        TextField tfEmail = new TextField();
        tfEmail.setPromptText("Email");
        labEmail.setLabelFor(labEmail);
        Platform.runLater(tfEmail::requestFocus);

        /* Link */

        Hyperlink btnEnterToken = new Hyperlink("Enter token ...");
        btnEnterToken.setOnAction(a -> showChangePasswordDialog());

        /* Pane */

        GridPane resetPane = new GridPane();
        resetPane.setPrefWidth(250);
        resetPane.setVgap(5);

        GridPane.setHgrow(tfEmail, Priority.ALWAYS);
        GridPane.setHalignment(btnEnterToken, HPos.RIGHT);

        int row = 0;
        resetPane.add(labEmail,      0, row++);
        resetPane.add(tfEmail,       0, row++);
        resetPane.add(btnEnterToken, 0, row);

        var result = Dialogs.builder()
                .buttons(new ButtonType("Reset password", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL)
                .title("Reset password")
                .content(resetPane)
                .build()
                .showAndWait();

        if (result.isEmpty() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }

        var success = EduAPI.requestPasswordResetToken(tfEmail.getText());

        if (success) {
            showChangePasswordDialog();
        } else {
            Dialogs.showErrorNotification("Error", "Error while resetting password. Please retry.");
        }
    }

    public void showChangePasswordDialog() {
        /* Text */

        Text txtReminder = new Text("Check your email for your password reset token.");
        txtReminder.setFont(Font.font(null, FontWeight.BOLD, -1)); // Default font, default size, bolded

        /* Textfields */

        Label labToken = new Label("Token");
        TextField tfToken = new TextField();
        tfToken.setPromptText("Token");
        labToken.setLabelFor(labToken);
        Platform.runLater(tfToken::requestFocus);

        Label labPassword = new Label("Password");
        TextField tFPassword = new TextField();
        tFPassword.setPromptText("Password");
        labPassword.setLabelFor(labPassword);

        Label labRepeatPassword = new Label("Repeat password");
        TextField tfRepeatPassword = new TextField();
        tfRepeatPassword.setPromptText("Repeat password");
        labRepeatPassword.setLabelFor(labRepeatPassword);

        /* Pane */

        GridPane resetPane = new GridPane();
        resetPane.setPrefWidth(250);
        resetPane.setVgap(5);

        GridPane.setHgrow(tfToken,          Priority.ALWAYS);
        GridPane.setHgrow(tFPassword,       Priority.ALWAYS);
        GridPane.setHgrow(tfRepeatPassword, Priority.ALWAYS);

        int row = 0;
        resetPane.add(txtReminder,       0, row++);
        resetPane.add(labToken,          0, row++);
        resetPane.add(tfToken,           0, row++);
        resetPane.add(labPassword,       0, row++);
        resetPane.add(tFPassword,        0, row++);
        resetPane.add(labRepeatPassword, 0, row++);
        resetPane.add(tfRepeatPassword,  0, row);

        var result = Dialogs.builder()
                .buttons(new ButtonType("Change password", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL)
                .title("Change password")
                .content(resetPane)
                .build()
                .showAndWait();

        if (result.isEmpty() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }

        var token = tfToken.getText();
        var password = tFPassword.getText();
        var repeatPassword = tfRepeatPassword.getText();

        if (!(password.equals(repeatPassword))) {
            Dialogs.showErrorNotification("Error", "Passwords do not match, please retry.");
            return;
        }

        var success = EduAPI.resetPassword(token, password);

        if (success) {
            Dialogs.showInfoNotification("Success", "Password changed successfully.");
        } else {
            Dialogs.showErrorNotification("Error", "Error while changing password. Please check that the token is valid and has not expired.");
        }
    }
}
