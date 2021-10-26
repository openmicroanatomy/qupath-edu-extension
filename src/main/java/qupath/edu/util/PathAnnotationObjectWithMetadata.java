package qupath.edu.util;

import qupath.lib.objects.MetadataStore;
import qupath.lib.objects.PathAnnotationObject;

import java.io.*;

import java.util.Map;
import java.util.Set;

public class PathAnnotationObjectWithMetadata extends PathAnnotationObject implements MetadataStore {

    public PathAnnotationObjectWithMetadata(PathAnnotationObject annotation) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);

            annotation.writeExternal(oos);

            oos.close();

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            readExternal(ois);
        } catch (Exception ignored) {}
    }

    @Override
    public Object putMetadataValue(String key, String value) {
        return storeMetadataValue(key, value);
    }

    @Override
    public String getMetadataString(String key) {
        return (String) retrieveMetadataValue(key);
    }

    @Override
    public Object getMetadataValue(String key) {
        return retrieveMetadataValue(key);
    }

    @Override
    public Set<String> getMetadataKeys() {
        return retrieveMetadataKeys();
    }

    @Override
    public Map<String, String> getMetadataMap() {
        return getUnmodifiableMetadataMap();
    }
}
