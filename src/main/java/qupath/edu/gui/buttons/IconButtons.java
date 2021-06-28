package qupath.edu.gui.buttons;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;

public class IconButtons {

    private final static GlyphFont awesome = GlyphFontRegistry.font("FontAwesome");

    public static Button createIconButton(FontAwesome.Glyph icon) {
        return createIconButton(icon, "", 12);
    }

    public static Button createIconButton(FontAwesome.Glyph icon, String text) {
        return createIconButton(icon, text, 12);
    }

    public static Button createIconButton(FontAwesome.Glyph icon, int iconSize) {
        return createIconButton(icon, "", iconSize);
    }

    public static Button createIconButton(FontAwesome.Glyph icon, String text, int iconSize) {
        Glyph glyph = awesome.create(icon).size(iconSize).color(Color.BLACK);

        Button button = new Button(null, glyph);

        if (!text.isBlank()) {
            button.setTooltip(new Tooltip(text));
        }

        return button;
    }
}
