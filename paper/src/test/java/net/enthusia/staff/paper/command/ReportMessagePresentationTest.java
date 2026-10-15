package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportMessagePresentationTest {
    @Test
    void pageTextRetainsFullReporterDescriptionAcrossUnicodeChunkBoundary() {
        String original = "X".repeat(179) + "😀" + "Y".repeat(2_000);
        List<String> segments = new ArrayList<>();
        ReportsCommand.appendMessageBody(segments, original);
        assertEquals(original, String.join("", segments));
        assertTrue(segments.stream().allMatch(segment -> segment.length() <= 180));
    }

    @Test
    void historicalOversizedMessageHasExplicitTruncationMarker() {
        List<String> segments = new ArrayList<>();
        ReportsCommand.appendMessageBody(segments, "X".repeat(4_100));
        assertTrue(segments.getLast().startsWith("Historical message exceeds"));
        assertEquals(4_000, String.join("", segments.subList(0, segments.size() - 1)).length());
    }

    @Test
    void reporterFollowupsMayBeReadByManagersButPrivateNotesRequireEvidencePermission() {
        assertTrue(ReportsCommand.restrictedOrigin(
                net.enthusia.staff.domain.report.ReportMessage.Origin.STAFF_ACTION));
        assertTrue(ReportsCommand.restrictedOrigin(
                net.enthusia.staff.domain.report.ReportMessage.Origin.UNKNOWN));
        assertFalse(ReportsCommand.restrictedOrigin(
                net.enthusia.staff.domain.report.ReportMessage.Origin.REPORTER_FOLLOWUP));
    }

    @Test
    void lineBreaksAreRenderedAsSafeTextNotExtraChatCommands() {
        List<String> segments = new ArrayList<>();
        ReportsCommand.appendMessageBody(segments, "Line one\n/ban all\rLine two");
        assertEquals("Line one /ban all Line two", String.join("", segments));
    }
}
