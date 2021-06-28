package qupath.edu.gui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import org.controlsfx.glyphfont.FontAwesome;
import qupath.edu.gui.buttons.IconButtons;
import qupath.edu.gui.dialogs.WorkspaceManager;
import qupath.edu.models.ExternalSubject;

public class SubjectListCell extends ListCell<ExternalSubject> {

    private final boolean hasWriteAccess;
    private final WorkspaceManager workspaceManager;

    public SubjectListCell(WorkspaceManager workspaceManager, boolean hasWriteAccess) {
        this.workspaceManager = workspaceManager;
        this.hasWriteAccess = hasWriteAccess;
    }

    @Override
    protected void updateItem(ExternalSubject item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            return;
        }

        /* Subject name */

        // TODO: Fix subject name may be wider than the SplitPane, thus hiding all buttons

        HBox name = new HBox(new Text(item.getName()));
        name.setFillHeight(true);
        name.setAlignment(Pos.CENTER_LEFT);

        /* Buttons */

        Button btnModify = IconButtons.createIconButton(FontAwesome.Glyph.EDIT, "Edit");
        btnModify.setOnAction(a -> workspaceManager.renameSubject(item));

        Button btnDelete = IconButtons.createIconButton(FontAwesome.Glyph.TRASH, "Delete");
        btnDelete.setOnAction(a -> workspaceManager.deleteSubject(item));

        HBox buttons = new HBox(btnModify, btnDelete);
        buttons.setSpacing(5D);
        buttons.setVisible(false);

        /* Pane */

        HBox hbox = new HBox(name, buttons);
        HBox.setHgrow(name, Priority.ALWAYS);
        hbox.setFillHeight(true);

        /* Hover */

        if (hasWriteAccess) {
            hbox.setOnMouseEntered(e -> buttons.setVisible(true));
            hbox.setOnMouseExited(e  -> buttons.setVisible(false));
        }

        setGraphic(hbox);
    }
}
