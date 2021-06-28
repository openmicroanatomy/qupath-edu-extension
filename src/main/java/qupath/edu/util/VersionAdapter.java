package qupath.edu.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import qupath.lib.gui.Version;

import java.io.IOException;

public class VersionAdapter extends TypeAdapter<Version> {

    public Version read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        try {
            return Version.parse(reader.nextString());
        } catch (IllegalArgumentException e) {
            return Version.UNKNOWN;
        }
    }

    public void write(JsonWriter writer, Version value) throws IOException {
        if (value == null) {
            writer.nullValue();
            return;
        }

        String version = value.toString();

        writer.value(version);
    }
}
