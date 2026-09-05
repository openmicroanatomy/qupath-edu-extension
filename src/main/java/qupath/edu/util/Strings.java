package qupath.edu.util;

public class Strings {
    public static String removeTrailingSlash(String str) {
        if (str.endsWith("/")) {
            return str.substring(0, str.length() - 1);
        } else {
            return str;
        }
    }
}
