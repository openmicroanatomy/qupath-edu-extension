package qupath.edu.util;

/**
 * todo
 *  - show "advanced annotations" only on "analysis mode"
 *  - disable "create lesson" etc on "analysis mode"
 */
public enum UserMode {


    STUDYING(false, false),
    ANALYSING(true, false),
    EDITING(true, true);

    private final boolean toolsEnabled;
    private final boolean readOnly;

    UserMode(boolean toolsEnabled, boolean readOnly) {
        this.toolsEnabled = toolsEnabled;
        this.readOnly = readOnly;
    }

    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
