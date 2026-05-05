package be.ehb.integrationbridge.exception;

public class XmlSerializationException extends RuntimeException {
    public XmlSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public XmlSerializationException(String message) {
        super(message);
    }
}
