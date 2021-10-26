package qupath.edu.models;

import javafx.beans.property.SimpleBooleanProperty;

import java.util.Map;

public class ExternalSlide {

    private String name;
    private String id;
    private ExternalOwner owner;
    private Map<String, String> properties;

    private SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public ExternalOwner getOwner() {
        return owner;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    @Override
    public String toString() {
        return "ExternalSlide{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", owner='" + owner + '\'' +
                ", properties=" + properties +
                '}';
    }
}
