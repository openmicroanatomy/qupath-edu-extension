package qupath.edu.gui;

import javafx.scene.control.ListCell;
import qupath.edu.models.Server;

public class ServerListCell extends ListCell<Server> {

    @Override
    protected void updateItem(Server item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
        } else {
            setText(item.getName());
        }
    }
}
