package qupath.edu.models;

import qupath.lib.gui.Version;

public class ServerConfiguration {

    private final Version version;
    private final boolean guestLoginEnabled;
    private final boolean simpleLoginEnabled;
    private final boolean microsoftLoginEnabled;

    public ServerConfiguration(Version version, boolean guestLoginEnabled, boolean simpleLoginEnabled, boolean microsoftLoginEnabled) {
        this.version = version;
        this.guestLoginEnabled = guestLoginEnabled;
        this.simpleLoginEnabled = simpleLoginEnabled;
        this.microsoftLoginEnabled = microsoftLoginEnabled;
    }

    public Version getVersion() {
        return version;
    }

    public boolean isGuestLoginEnabled() {
        return guestLoginEnabled;
    }

    public boolean isSimpleLoginEnabled() {
        return simpleLoginEnabled;
    }

    public boolean isMicrosoftLoginEnabled() {
        return microsoftLoginEnabled;
    }
}
