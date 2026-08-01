package net.enthusia.staff.paper.config;

import java.util.List;

public final class PaperConfigurationValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    public PaperConfigurationValidationException(List<String> errors) {
        super(summary(errors));
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("configuration validation errors must be present");
        }
        this.errors = List.copyOf(errors);
    }

    public PaperConfigurationValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of(message);
    }

    public List<String> errors() {
        return errors;
    }

    private static String summary(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Configuration validation failed";
        }
        return "Configuration validation failed: " + errors.getFirst();
    }
}
