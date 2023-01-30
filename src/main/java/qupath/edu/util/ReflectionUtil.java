package qupath.edu.util;

import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.objects.PathObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtil {

    /* QuPathGUI: Analysis Panel */

    public static void setAnalysisPaneVisible(boolean visible) {
        try {
            Field fMainPaneManager = QuPathGUI.class.getDeclaredField("mainPaneManager");
            fMainPaneManager.setAccessible(true);
            var mainPaneManager = fMainPaneManager.get(QuPathGUI.getInstance());

            Class clazz = Class.forName("qupath.lib.gui.QuPathMainPaneManager");
            Method method = clazz.getDeclaredMethod("setAnalysisPaneVisible", boolean.class);
            method.setAccessible(true);
            method.invoke(mainPaneManager, visible);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }
    }

    /* QuPathGUI: Main Viewer Pane */

    public static Region getMainViewerPane() {
        try {
            Field field = QuPathGUI.class.getDeclaredField("mainPaneManager");
            field.setAccessible(true);
            var mainPaneManager = field.get(QuPathGUI.getInstance());

            Class clazz = Class.forName("qupath.lib.gui.QuPathMainPaneManager");
            Field field2 = clazz.getDeclaredField("mainViewerPane");
            field2.setAccessible(true);
            return (Region) field2.get(mainPaneManager);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    public static void setMainViewerPane(Region node) {
        try {
            Field fMainPaneManager = QuPathGUI.class.getDeclaredField("mainPaneManager");
            fMainPaneManager.setAccessible(true);
            var mainPaneManager = fMainPaneManager.get(QuPathGUI.getInstance());

            Class clazz = Class.forName("qupath.lib.gui.QuPathMainPaneManager");
            Field fMainPane = clazz.getDeclaredField("mainViewerPane");
            fMainPane.setAccessible(true);
            fMainPane.set(mainPaneManager, node);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }
    }

    /* QuPathGUI: Project Browser */

    public static ProjectBrowser getProjectBrowser() {
        try {
            Field fMainPaneManager = QuPathGUI.class.getDeclaredField("mainPaneManager");
            fMainPaneManager.setAccessible(true);
            var mainPaneManager = fMainPaneManager.get(QuPathGUI.getInstance());

            Class clazz = Class.forName("qupath.lib.gui.QuPathMainPaneManager");
            Method method = clazz.getDeclaredMethod("getProjectBrowser");
            method.setAccessible(true);
            return (ProjectBrowser) method.invoke(mainPaneManager);
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }

    /* QuPathGUI: loadIcon */

    public static Image loadIcon(int size) {
        try {
            Method method = QuPathGUI.class.getDeclaredMethod("loadIcon", int.class);
            method.setAccessible(true);
            return (Image) method.invoke(QuPathGUI.getInstance(), size);
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

    /* ProjectBrowser: getTree() */

    public static TreeView<Object> getProjectBrowserTree() {
        try {
            Field field = ProjectBrowser.class.getDeclaredField("tree");
            field.setAccessible(true);
            return (TreeView<Object>) field.get(getProjectBrowser());
        } catch (Exception e) {
            Dialogs.showErrorNotification("Reflection exception. Please report this error via Github", e);
        }

        return null;
    }
}
