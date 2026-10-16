package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

final class ReportGuiDescriptionPreviewTest {
    private static final UUID REPORT_ID =
            UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Test
    void shortDescriptionRemainsVisibleWithoutTruncationMessage() {
        assertEquals(
                java.util.List.of(Component.text("Reported actions and context.", NamedTextColor.GRAY)),
                ReportGuiRenderer.descriptionPreview("Reported actions and context.", REPORT_ID));
    }

    @Test
    void exactPreviewBoundaryDoesNotClaimTruncation() {
        var lines = ReportGuiRenderer.descriptionPreview("a".repeat(480), REPORT_ID);
        assertEquals(10, lines.size());
        assertEquals(Component.text("a".repeat(48), NamedTextColor.GRAY), lines.get(9));
    }

    @Test
    void mergedReportHistoryLinkSurvivesShortAndTruncatedDescriptionPreviews() {
        var shortLore = ReportGuiRenderer.descriptionWithHistory("original", REPORT_ID);
        var longLore = ReportGuiRenderer.descriptionWithHistory("x".repeat(481), REPORT_ID);
        Component history = Component.text("/reports messages " + REPORT_ID, NamedTextColor.WHITE);
        Component full = Component.text("/reports view " + REPORT_ID, NamedTextColor.WHITE);
        assertEquals(history, shortLore.getLast());
        assertEquals(history, longLore.getLast());
        assertEquals(full, longLore.get(longLore.size() - 4));
    }

    @Test
    void longerDescriptionsPointToTheFullPrivateReportText() {
        var lines = ReportGuiRenderer.descriptionPreview("b".repeat(481), REPORT_ID);
        assertEquals(12, lines.size());
        assertEquals(
                Component.text("Preview truncated. Read the full description:", NamedTextColor.YELLOW),
                lines.get(10));
        assertEquals(
                Component.text("/reports view " + REPORT_ID, NamedTextColor.WHITE),
                lines.get(11));
    }
}
