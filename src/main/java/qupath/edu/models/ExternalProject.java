package qupath.edu.models;

public class ExternalProject {

    private String id;
    private String name;
    private String description;
    private String subject;
    private String owner;
    private String timestamp;
    private boolean hidden;

    public String getId() {
        return id;
    }

    public String getIdWithTimestamp() {
        if (hasTimestamp()) {
            return id + ":" + timestamp;
        }

        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public boolean hasTimestamp() {
        return timestamp != null;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
