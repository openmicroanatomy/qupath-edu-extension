package qupath.edu.gui.dialogs;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import qupath.edu.api.EduAPI;

public final class EditModeInfoPopOverDialog {

    private static final int PANE_WIDTH = 250;

    private final static String CONNECTED    = "If you wish to make any changes to this lesson, enable editing first. Changes made by guests are discarded.";
    private final static String DISCONNECTED = "You're not connected to any server. Edit mode is only available when connected to a server.";

    public static Pane getPane() {
        GridPane pane = new GridPane();
        pane.setMaxWidth(PANE_WIDTH);
        pane.setPadding(new Insets(5));
        pane.setVgap(10);

        Text txtStatus = new Text("Status: ");

        Text txtState = new Text();
        txtState.textProperty().bind(Bindings.when(EduAPI.connectedToServerProperty()).then("Connected").otherwise("Disconnected"));
        txtState.fillProperty().bind(Bindings.when(EduAPI.connectedToServerProperty()).then(Color.GREEN).otherwise(Color.RED));

        Text txtInfo = new Text();
        txtInfo.textProperty().bind(Bindings.when(EduAPI.connectedToServerProperty()).then(CONNECTED).otherwise(DISCONNECTED));
        txtInfo.setWrappingWidth(PANE_WIDTH);

        GridPane.setColumnSpan(txtInfo, 2);

        pane.add(txtStatus, 0, 0);
        pane.add(txtState,  1, 0);
        pane.add(txtInfo,   0, 1);

        return pane;
    }
}
