package qupath.edu;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.prefs.PathPrefs;

public class EduOptions {

    /**
     * Flag to indicate whether extension is enabled or not
     */
    public static BooleanProperty extensionEnabled() {
        return extensionEnabled;
    }

    private static final BooleanProperty extensionEnabled = PathPrefs.createPersistentPreference("eduExtensionEnabled", true);

    /**
     * Flag to indicate whether to show workspace dialog on startup
     */
    public static BooleanProperty showLoginDialogOnStartup() {
        return showLoginDialog;
    }

    private static final BooleanProperty showLoginDialog = PathPrefs.createPersistentPreference("eduShowLoginDialog", true);

    /**
     * ID of the workspace which the user previously had open.
     */
    public static StringProperty previousWorkspace() {
        return previousWorkspace;
    }

    private static final StringProperty previousWorkspace = PathPrefs.createPersistentPreference("eduPreviousProject", null);

    /**
     * ID of the organization that was previously selected on the workspace manager dialog.
     */
    public static StringProperty previousOrganization() {
        return previousOrganization;
    }

    private static final StringProperty previousOrganization = PathPrefs.createPersistentPreference("eduPreviousOrganization", null);

    /**
     * Host used to communicate with.
     */
    public static StringProperty host() {
        return host;
    }

    private static final StringProperty host = PathPrefs.createPersistentPreference("eduHost", null);

}
