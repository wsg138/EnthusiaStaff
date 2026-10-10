package net.enthusia.staff.paper.aireview;

/** Fixed allowlisted server-side history filters (not free-text SQL). */
enum AiReviewHistoryFilter {
    ALL("all", "All"),
    ALLOWED("allowed", "Allowed"),
    BLOCKED("blocked", "Blocked"),
    REVIEW("review", "Needs Review"),
    FAIL_OPEN("fail_open", "Fail-open"),
    CORRECTED("corrected", "Corrected");

    private final String apiValue;
    private final String displayName;

    AiReviewHistoryFilter(String apiValue, String displayName) {
        this.apiValue = apiValue;
        this.displayName = displayName;
    }

    String apiValue() {
        return apiValue;
    }

    String displayName() {
        return displayName;
    }

    AiReviewHistoryFilter next() {
        AiReviewHistoryFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
