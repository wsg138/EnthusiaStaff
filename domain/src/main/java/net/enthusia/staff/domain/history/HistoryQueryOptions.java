package net.enthusia.staff.domain.history;

public record HistoryQueryOptions(
        boolean includeRequestEvents,
        boolean includeAppealEvents,
        boolean includeSensitive
) {
    public static HistoryQueryOptions publicStaffView(
            boolean includeRequestEvents,
            boolean includeAppealEvents
    ) {
        return new HistoryQueryOptions(includeRequestEvents, includeAppealEvents, false);
    }
}
