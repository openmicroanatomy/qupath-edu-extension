package qupath.edu.models;

import qupath.lib.gui.Version;

import java.util.List;

public class VersionHistory {

    private Release latest;
    private List<Release> versions;

    public Release getLatest() {
        return latest;
    }

    public List<Release> getVersions() {
        return versions;
    }

    public static class Release {

        private Integer id;
        private Version version;
        private String compatible;
        private String releaseNotes;
        private String downloadUrl;

        public Integer getId() {
            return id;
        }

        public Version getVersion() {
            return version;
        }

        public String getCompatible() {
            return compatible;
        }

        public String getReleaseNotes() {
            return releaseNotes;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }
    }
}
