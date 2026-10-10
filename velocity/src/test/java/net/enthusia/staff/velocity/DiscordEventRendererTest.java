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
    void punishmentChannelNeverRevealsPrivateAltRelationshipMetadata() {
        String payload = "{\"caseId\":\"case-1\",\"targetId\":\"target\","
                + "\"sourcePlayerId\":\"private-source\",\"sourceSanctionId\":\"private-sanction\","
                + "\"relationshipState\":\"CONFIRMED_ALT\"}";
        String punishment = renderer.render(message("punishments", "SANCTION_INHERITED", payload));
        assertTrue(punishment.contains("caseId=case-1"));
        assertFalse(punishment.contains("private-source"));
        assertFalse(punishment.contains("private-sanction"));
        assertFalse(punishment.contains("relationshipState"));
        String alert = renderer.render(message("alerts", "ALT_EVASION_REVIEW", payload));
        assertTrue(alert.contains("sourcePlayerId=private-source"));
        assertTrue(alert.contains("relationshipState=CONFIRMED_ALT"));
    }

    @Test
    void freezeEventsRenderUnderPunishmentsWithReason() {
        String rendered = renderer.render(message(
                "punishments",
                "PLAYER_FROZEN",
                "{\"targetId\":\"target\",\"reason\":\"line1\\nline2 `code`\"}"
        ));

        assertTrue(rendered.contains("reason=line1 line2 'code'"));
        assertFalse(rendered.contains("`"));
    }

    @Test
    void altReviewAlertShowsEvidenceCategoryWithoutRawNetworkAddress() {
        String rendered = renderer.render(message(
                "alerts", "ALT_EVASION_REVIEW",
                "{\"targetId\":\"joining\",\"relatedPlayerId\":\"sanctioned\","
                        + "\"sanctionType\":\"BAN\",\"relationshipState\":\"SAME_NETWORK\","
                        + "\"confidencePolicyGrade\":0.25,\"trigger\":\"JOIN\","
                        + "\"rawIp\":\"private-address\",\"caseId\":\"case-7\"}"
        ));
        assertTrue(rendered.contains("relatedPlayerId=sanctioned"));
        assertTrue(rendered.contains("trigger=JOIN"));
        assertTrue(rendered.contains("confidencePolicyGrade=0.25"));
        assertFalse(rendered.contains("private-address"));
        assertFalse(rendered.contains("rawIp"));
    }

    @Test
    void approvalRequiredAlertExposesOnlySafeReviewMetadata() {
        String rendered = renderer.render(message(
                "alerts", "PUNISHMENT_APPROVAL_REQUIRED",
                "{\"requestId\":\"request-1\",\"targetId\":\"target-1\","
                        + "\"requesterId\":\"staff-1\",\"reasonId\":\"cheating\","
                        + "\"requiredRank\":\"MOD\",\"visibility\":\"PRIVATE\","
                        + "\"internalExplanation\":\"Sensitive evidence: do not post\"}"
        ));
        assertTrue(rendered.contains("PUNISHMENT_APPROVAL_REQUIRED"));
        assertTrue(rendered.contains("requestId=request-1"));
        assertTrue(rendered.contains("requiredRank=MOD"));
        assertFalse(rendered.contains("Sensitive evidence"));
        assertFalse(rendered.contains("internalExplanation"));
    }

    @Test
    void fieldTruncationDoesNotSplitSurrogatePair() {
        String reason = "a".repeat(178) + "😀" + "z";
        String rendered = renderer.render(message(
                "punishments",
                "PLAYER_FROZEN",
                "{\"reason\":\"" + reason + "\"}"
        ));

        assertTrue(rendered.endsWith("…"));
        assertFalse(Character.isHighSurrogate(rendered.charAt(rendered.length() - 2)));
    }

    @Test
    void normalizationCannotHideSurrogatePairSplitAtRawBoundary() {
        String normalizedTail = "a".repeat(79) + "😀z";
        String reason = " ".repeat(100) + normalizedTail;
        String rendered = renderer.render(message(
                "logs-staffmode",
                "PLAYER_FROZEN",
                "{\"reason\":\"" + reason + "\"}"
        ));

        assertTrue(rendered.endsWith("reason=" + normalizedTail));
    }

    @Test
    void unmatchedPayloadSurrogateIsReplacedBeforeDelivery() {
        String escapedHighSurrogate = "\\u" + "D83D";
        String rendered = renderer.render(message(
                "logs-staffmode",
                "PLAYER_FROZEN",
                "{\"reason\":\"before" + escapedHighSurrogate + "after\"}"
        ));

        assertTrue(rendered.endsWith("reason=before�after"));
    }

    @Test
    void contentTruncationDoesNotSplitSurrogatePair() {
        String value = "a".repeat(1_798) + "😀" + "z";
        String rendered = DiscordEventRenderer.truncateWithEllipsis(value, 1_800);

        assertTrue(rendered.endsWith("…"));
        assertFalse(Character.isHighSurrogate(rendered.charAt(rendered.length() - 2)));
        assertTrue(rendered.length() <= 1_800);
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
