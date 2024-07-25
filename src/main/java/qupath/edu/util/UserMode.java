package qupath.edu.util;

public enum UserMode {

    STUDYING(false, true),
    ANALYSING(true, false),
    EDITING(true, false);

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
