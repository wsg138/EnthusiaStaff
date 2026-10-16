package net.enthusia.staff.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportMessageTest {
    @Test
    void messageRejectsInvalidAttributionOrEmptyBody() {
        UUID messageId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T12:00:00Z");
        assertThrows(NullPointerException.class, () -> new ReportMessage(
                messageId, actor, "body", now, null));
        assertThrows(IllegalArgumentException.class, () -> new ReportMessage(
                messageId, actor, " ", now, ReportMessage.Origin.UNKNOWN));
        assertEquals(ReportMessage.Origin.REPORTER_FOLLOWUP,
                new ReportMessage(messageId, actor, "body", now,
                        ReportMessage.Origin.REPORTER_FOLLOWUP).origin());
    }

    @Test
    void pageIsAnImmutableBoundedView() {
        List<ReportMessage> mutable = new ArrayList<>();
        var page = new ReportMessagesPage(1, mutable, false);
        mutable.add(new ReportMessage(UUID.randomUUID(), UUID.randomUUID(), "staff note",
                Instant.parse("2026-10-15T12:00:00Z"), ReportMessage.Origin.STAFF_ACTION));
        assertTrue(page.messages().isEmpty());
        assertFalse(page.hasMore());
        assertThrows(IllegalArgumentException.class, () -> new ReportMessagesPage(0, List.of(), false));
        assertThrows(UnsupportedOperationException.class, () -> page.messages().add(mutable.getFirst()));
    }
}
