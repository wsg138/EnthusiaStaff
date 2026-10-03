package net.enthusia.staff.paper.aireview;

final class AiReviewClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    enum Category {
        TIMEOUT,
        CONFLICT,
        AUTH,
        UNAVAILABLE,
        OVERSIZED,
        MALFORMED,
        HTTP,
        NETWORK
    }

    private final Category category;

    AiReviewClientException(Category category) {
        super("AI review service request failed: " + category.name().toLowerCase(java.util.Locale.ROOT));
        this.category = category;
    }

    AiReviewClientException(Category category, Throwable cause) {
        super("AI review service request failed: " + category.name().toLowerCase(java.util.Locale.ROOT), cause);
        this.category = category;
    }

    Category category() {
        return category;
    }
}
