package net.enthusia.staff.domain.website;

public final class WebsiteModerationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        INVALID,
        NOT_FOUND,
        CONFLICT,
        INELIGIBLE,
        RATE_LIMITED,
        UNAVAILABLE
    }

    private final Kind kind;
    private final String code;

    public WebsiteModerationException(Kind kind, String code, String message) {
        super(message);
        if (kind == null || code == null || !code.matches("[A-Z0-9_]{3,64}")
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Website moderation error fields are invalid");
        }
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
