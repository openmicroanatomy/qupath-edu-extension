package qupath.edu.gui.dialogs;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.controlsfx.glyphfont.FontAwesome;
import qupath.edu.gui.buttons.IconButtons;
import qupath.edu.gui.dialogs.openmicroanatomy.*;
import qupath.fx.dialogs.Dialogs;

public class AdministrativeToolsDialog {

    private GridPane pane;
    private static Dialog<ButtonType> dialog;

    public static void show() {
        AdministrativeToolsDialog manager = new AdministrativeToolsDialog();

        dialog = Dialogs.builder()
            .title("Administrative tools")
            .content(manager.getPane())
            .buttons(ButtonType.CLOSE)
            .build();

        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
    }

    public synchronized GridPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);

        pane.add(createButton(
            FontAwesome.Glyph.BUILDING,
            "Organizations",
            createAction(OrganizationManager::show)
        ), 0, 0);

        pane.add(createButton(
            FontAwesome.Glyph.LOCK,
            "Permissions",
            createAction(PermissionManager::show)
        ),   1, 0);

        pane.add(createButton(
            FontAwesome.Glyph.USER,
            "Users",
            createAction(UserManager::show)
        ), 2, 0);

        pane.add(createButton(
            FontAwesome.Glyph.IMAGE,
            "Slides",
            createAction(SlideManager::show)
        ),  0, 1);

        pane.add(createButton(
            FontAwesome.Glyph.CLOUD_UPLOAD,
            "Backups",
            createAction(BackupManager::show)
        ), 1, 1);
    }

    private Node createButton(FontAwesome.Glyph icon, String text, EventHandler<ActionEvent> action) {
        var glyph = IconButtons.createIconButton(icon, text, 50);
        glyph.setStyle("-fx-background-color: transparent");

        var label = new Text(text);

        var item = new VBox(glyph, label);
        item.setAlignment(Pos.CENTER);

        var button = new Button();
        button.setGraphic(item);
        button.setPrefWidth(100);
        button.setPrefHeight(100);
        button.setOnAction(action);
        button.setCursor(Cursor.HAND);

        return button;
    }

    private EventHandler<ActionEvent> createAction(Runnable action) {
        return event -> {
            dialog.close();
            action.run();
        };
    }
}
