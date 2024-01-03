package qupath.edu.gui;

import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import qupath.edu.util.UserMode;

public class UserModeListCell extends ListCell<UserMode> {

    private final boolean showDescription;

    private final GlyphFont awesome = GlyphFontRegistry.font("FontAwesome");

    public UserModeListCell(boolean showDescription) {
        this.showDescription = showDescription;
    }

    @Override
    protected void updateItem(UserMode item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
        } else {
            Glyph icon = null;
            String text = "", description = "";

            setFont(Font.font(10));

            switch (item) {
                case STUDYING:
                    text = "Study mode";
                    description = "View lessons, but make no changes.";
                    icon = awesome.create(FontAwesome.Glyph.EYE).size(16).color(Color.BLACK);
                    break;
                case ANALYSING:
                    text = "Analysis mode";
                    description = "Enable all analysis tools.";
                    icon = awesome.create(FontAwesome.Glyph.BAR_CHART).size(16).color(Color.BLACK);
                    break;
                case EDITING:
                    // todo: limit to only users with write access
                    text = "Editing mode";
                    description = "Make any changes to lessons.";
                    icon = awesome.create(FontAwesome.Glyph.PENCIL).size(16).color(Color.BLACK);
                    break;
            }

            if (showDescription) {
                setText(text);
                setGraphic(icon);
            } else {
                var pane = new GridPane();

                icon.setPrefWidth(20);
                icon.setPrefHeight(20);

                var title    = new Text(text);
                var subtitle = new Text(description);
                subtitle.setFont(Font.font(10));

                pane.add(icon, 0, 0);
                pane.add(title, 1, 0);
                pane.add(subtitle, 1, 1);

                var vbox = new VBox(title, subtitle);
                var hbox = new HBox(icon, vbox);

                hbox.setSpacing(10);

                setGraphic(hbox);
            }
        }
    }
}
