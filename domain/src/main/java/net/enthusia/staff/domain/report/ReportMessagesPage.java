package net.enthusia.staff.domain.report;

import java.util.List;
import java.util.Objects;

/** Bounded, chronological staff case-message page. */
public record ReportMessagesPage(int page, List<ReportMessage> messages, boolean hasMore) {
    private static final int FIRST_PAGE = 1;
    public ReportMessagesPage {
        if (page < FIRST_PAGE) {
            throw new IllegalArgumentException("Page must be positive");
        }
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }
}
