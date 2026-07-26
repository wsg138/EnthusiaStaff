package net.enthusia.staff.paper.config;

public final class ConfigurationValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public ConfigurationValidationException(String message) {
        super(message);
    }

    public ConfigurationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
