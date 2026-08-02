package net.enthusia.staff.domain.history;

import java.util.List;
import java.util.UUID;

public record ModerationHistoryPage(
        UUID subjectId,
        int page,
        int pageSize,
        long totalEntries,
        int totalPages,
        List<ModerationHistoryEntry> entries
) {
    public ModerationHistoryPage {
        if (subjectId == null || page < 1 || pageSize < 1 || totalEntries < 0 || totalPages < 0
                || entries == null || entries.size() > pageSize) {
            throw new IllegalArgumentException("history page fields are invalid");
        }
        if (totalEntries == 0 && totalPages != 0) {
            throw new IllegalArgumentException("empty history must have zero total pages");
        }
        if (totalEntries > 0 && (totalPages < 1 || page > totalPages)) {
            throw new IllegalArgumentException("history page is outside the available range");
        }
        entries = List.copyOf(entries);
    }
}
