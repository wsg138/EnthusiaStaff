package net.enthusia.staff.domain.report;

import java.util.List;
import java.util.Objects;

/** Bounded, chronological staff case-message page. */
public record ReportMessagesPage(int page, List<ReportMessage> messages, boolean hasMore) {
    public ReportMessagesPage {
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }
}
