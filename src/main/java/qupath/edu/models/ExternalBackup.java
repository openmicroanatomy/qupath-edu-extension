package qupath.edu.models;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ExternalBackup {

    private String filename;
    private String baseName;
    private String timestamp;
    private String readable;
    private String type;

    public String getFilename() {
        return filename;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH.mm.ss");
            Date date = new Date(Long.parseLong(timestamp));

            return formatter.format(date);
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }

    public String getReadable() {
        return readable;
    }

    public String getType() {
        return type;
    }
}
