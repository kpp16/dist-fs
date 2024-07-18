package exceptions;

public class LocationDoesNotExistException extends Exception {
    public LocationDoesNotExistException(String message) {
        super(message);
    }

    public LocationDoesNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
