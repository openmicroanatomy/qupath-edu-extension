package qupath.edu.util;

import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ReflectionUtil {

    /* QuPathGUI: Analysis Panel */

    public static TabPane getAnalysisPanel() {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Field field = QuPathGUI.class.getDeclaredField("analysisPanel");
            field.setAccessible(true);
            return (TabPane) field.get(qupath);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    public static void setAnalysisPaneVisible(boolean visible) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Method method = QuPathGUI.class.getDeclaredMethod("setAnalysisPaneVisible", boolean.class);
            method.setAccessible(true);
            method.invoke(qupath, visible);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }
    }

    /* QuPathGUI: Main Viewer Pane */

    public static Region getMainViewerPane() {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Field field = QuPathGUI.class.getDeclaredField("mainViewerPane");
            field.setAccessible(true);
            return (Region) field.get(qupath);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    public static void setMainViewerPane(Region node) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Field field = QuPathGUI.class.getDeclaredField("mainViewerPane");
            field.setAccessible(true);
            field.set(qupath, node);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }
    }

    /* QuPathGUI: Project Browser */

    public static ProjectBrowser getProjectBrowser() {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Field field = QuPathGUI.class.getDeclaredField("projectBrowser");
            field.setAccessible(true);
            return (ProjectBrowser) field.get(qupath);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    /* QuPathGUI: loadIcon */

    public static Image loadIcon(int size) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Method method = QuPathGUI.class.getDeclaredMethod("loadIcon", int.class);
            method.setAccessible(true);
            return (Image) method.invoke(qupath, size);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    /* QuPathGUI: checkSaveChanges */

    public static Boolean checkSaveChanges(ImageData<BufferedImage> imageData) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Method method = QuPathGUI.class.getDeclaredMethod("checkSaveChanges", ImageData.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(qupath, imageData);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    /* QuPathViewerPlus: basePane */

    public static AnchorPane getViewerBasePane(QuPathViewerPlus viewer) {
        try {
            Field field = QuPathViewerPlus.class.getDeclaredField("basePane");
            field.setAccessible(true);
            return (AnchorPane) field.get(viewer);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    /* PathObject: Metadata */

    public static Object storeMetadataValue(PathObject object, String key, String value) {
        try {
            Method method = PathObject.class.getDeclaredMethod("storeMetadataValue", String.class, String.class);
            method.setAccessible(true);
            return method.invoke(object, key, value);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    public static Object retrieveMetadataValue(PathObject object, String key) {
        try {
            Method method = PathObject.class.getDeclaredMethod("retrieveMetadataValue", String.class);
            method.setAccessible(true);
            return method.invoke(object, key);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }
}
