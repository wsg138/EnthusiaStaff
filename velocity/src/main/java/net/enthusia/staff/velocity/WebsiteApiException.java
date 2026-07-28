package net.enthusia.staff.velocity;

final class WebsiteApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    WebsiteApiException(int status, String code, String message) {
        super(message);
        if (status < 400 || status > 599 || code == null || !code.matches("[A-Z0-9_]{3,64}")
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Website API error fields are invalid");
        }
        this.status = status;
        this.code = code;
    }

    int status() {
        return status;
    }

    String code() {
        return code;
    }
}
