package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.discord.DiscordOutboxMessage;
import org.junit.jupiter.api.Test;

final class DiscordEventRendererTest {
    private static final String REPORTS = "reports";
    private static final String REPORT_CREATED = "REPORT_CREATED";

    private final DiscordEventRenderer renderer = new DiscordEventRenderer();

    @Test
    void reportRenderingOmitsReporterAndNestedEvidence() {
        String rendered = renderer.render(message(
                REPORTS,
                REPORT_CREATED,
                "{\"reportId\":\"r-1\",\"reporterId\":\"private-reporter\","
                        + "\"targetId\":\"target-1\",\"reasonId\":\"spam\",\"serverId\":\"SMP\","
                        + "\"targetClientEvidence\":{\"client\":\"sensitive\"},"
                        + "\"description\":\"private body\"}"
        ));

        assertTrue(rendered.contains(REPORT_CREATED));
        assertTrue(rendered.contains("reportId=r-1"));
        assertTrue(rendered.contains("targetId=target-1"));
        assertTrue(rendered.contains("reasonId=spam"));
        assertTrue(rendered.contains("serverId=SMP"));
        assertFalse(rendered.contains("private-reporter"));
        assertFalse(rendered.contains("targetClientEvidence"));
        assertFalse(rendered.contains("sensitive"));
        assertFalse(rendered.contains("private body"));
    }

    @Test
    void punishmentArraysAreBoundedAndNestedValuesAreWithheld() {
        String rendered = renderer.render(message(
                "punishments",
                "PUNISHMENT_CREATED",
                "{\"caseId\":\"case-1\",\"sanctionIds\":[\"1\",\"2\",\"3\",\"4\",\"5\","
                        + "\"6\",\"7\",\"8\",\"9\"],\"reasonId\":{\"raw\":\"secret\"}}"
        ));

        assertTrue(rendered.contains("caseId=case-1"));
        assertTrue(rendered.contains("sanctionIds=1, 2, 3, 4, 5, 6, 7, 8"));
        assertFalse(rendered.contains(", 9"));
        assertFalse(rendered.contains("secret"));
    }

    @Test
    void staffLogTextIsNormalizedAndBounded() {
        String rendered = renderer.render(message(
                "logs-staffmode",
                "PLAYER_FROZEN",
                "{\"targetId\":\"target\",\"reason\":\"line1\\nline2 `code`\"}"
        ));

        assertTrue(rendered.contains("reason=line1 line2 'code'"));
        assertFalse(rendered.contains("`"));
    }

    @Test
    void malformedNonObjectAndOversizedPayloadsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(message(REPORTS, REPORT_CREATED, "not-json"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(message(REPORTS, REPORT_CREATED, "[1,2,3]"))
        );
        String oversized = "{\"reportId\":\"" + "x".repeat(16_500) + "\"}";
        assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(message(REPORTS, REPORT_CREATED, oversized))
        );
    }

    @Test
    void unknownDestinationHasNoFallbackRenderingPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(message("unknown", "UNKNOWN", "{}"))
        );
    }

    private static DiscordOutboxMessage message(String destination, String eventType, String payload) {
        return new DiscordOutboxMessage(
                UUID.randomUUID(),
                destination,
                eventType,
                payload,
                0,
                Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
