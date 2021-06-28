package qupath.edu.models;

public class ExternalError {

    private String error;

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ExternalError{" +
                "error='" + error + '\'' +
                '}';
    }
}
