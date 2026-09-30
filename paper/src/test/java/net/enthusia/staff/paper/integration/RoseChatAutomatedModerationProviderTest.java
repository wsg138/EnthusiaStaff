package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.staff.AutomatedModerationEvidence;
import dev.rosewood.rosechat.api.staff.AutomatedPublicMuteRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class RoseChatAutomatedModerationProviderTest {
    private static final UUID TARGET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST_EVENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND_EVENT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant FIRST_AT = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void aiPolicyIsAutomaticPublicThirtyDayMute() {
        ReasonPolicy policy = RoseChatAutomatedModerationProvider.policy();

        assertEquals("chat.ai-moderation", policy.id());
        assertTrue(policy.automaticDetectionAllowed());
        assertTrue(policy.publicByDefault());
        assertEquals(AltInheritanceMode.NONE, policy.altInheritance());
        assertEquals(1, policy.steps().size());
        assertEquals(1, policy.steps().getFirst().sanctions().size());

        SanctionSpec sanction = policy.steps().getFirst().sanctions().getFirst();
        assertEquals(SanctionType.PUBLIC_MUTE, sanction.type());
        assertEquals(SanctionLength.Kind.TEMPORARY, sanction.length().kind());
        assertEquals(Duration.ofDays(30), sanction.length().temporary().orElseThrow());
    }

    @Test
    void exactlyTwoStrikesAreRequiredByProviderPolicy() {
        AutomatedPublicMuteRequest accepted = request(List.of(
                evidence(FIRST_EVENT, FIRST_AT, "first", 0.75D, 70),
                evidence(SECOND_EVENT, FIRST_AT.plusSeconds(30), "second", 0.9D, 90)
        ));
        AutomatedPublicMuteRequest tooMany = request(List.of(
                evidence(UUID.randomUUID(), FIRST_AT, "one", 0.75D, 70),
                evidence(UUID.randomUUID(), FIRST_AT, "two", 0.8D, 80),
                evidence(UUID.randomUUID(), FIRST_AT, "three", 0.9D, 90)
        ));

        assertNull(AutomatedModerationRequestPolicy.rejectionReason(accepted));
        assertEquals(
                "Exactly two enforcement strikes are required",
                AutomatedModerationRequestPolicy.rejectionReason(tooMany)
        );
    }

    @Test
    void evidenceExplanationContainsEveryReviewableStrike() {
        AutomatedPublicMuteRequest request = request(List.of(
                evidence(FIRST_EVENT, FIRST_AT, "first exact message", 0.75D, 70),
                evidence(SECOND_EVENT, FIRST_AT.plusSeconds(30), "second exact message", 0.9D, 90)
        ));

        String explanation = AutomatedModerationEvidenceFormatter.format(request);

        assertTrue(explanation.contains("event=" + FIRST_EVENT));
        assertTrue(explanation.contains("Exact message: first exact message"));
        assertTrue(explanation.contains("event=" + SECOND_EVENT));
        assertTrue(explanation.contains("Exact message: second exact message"));
        assertTrue(explanation.contains("confidence=0.9"));
    }

    @Test
    void maximumTwoStrikeEvidenceIsStoredWithoutTruncation() {
        String firstMessage = "a".repeat(1_024);
        String secondMessage = "b".repeat(1_024);
        AutomatedPublicMuteRequest request = request(List.of(
                evidence(FIRST_EVENT, FIRST_AT, firstMessage, 0.8D, 80),
                evidence(SECOND_EVENT, FIRST_AT.plusSeconds(30), secondMessage, 0.9D, 90)
        ));

        String explanation = AutomatedModerationEvidenceFormatter.format(request);

        assertTrue(explanation.length() <= 4_000);
        assertTrue(explanation.contains("Exact message: " + firstMessage));
        assertTrue(explanation.contains("Exact message: " + secondMessage));
    }

    @Test
    void nonPolicyOversizedEvidenceFailsInsteadOfDroppingRecords() {
        String longMessage = "x".repeat(1_024);
        AutomatedPublicMuteRequest request = request(List.of(
                evidence(UUID.fromString("40000000-0000-0000-0000-000000000001"), FIRST_AT, longMessage, 0.8D, 80),
                evidence(UUID.fromString("40000000-0000-0000-0000-000000000002"), FIRST_AT, longMessage, 0.8D, 80),
                evidence(UUID.fromString("40000000-0000-0000-0000-000000000003"), FIRST_AT, longMessage, 0.8D, 80),
                evidence(UUID.fromString("40000000-0000-0000-0000-000000000004"), FIRST_AT, longMessage, 0.8D, 80)
        ));

        assertThrows(IllegalArgumentException.class, () -> AutomatedModerationEvidenceFormatter.format(request));
    }

    private static AutomatedModerationEvidence evidence(
            UUID eventId,
            Instant at,
            String message,
            double confidence,
            int severity
    ) {
        return new AutomatedModerationEvidence(
                eventId,
                at,
                message,
                "harassment",
                confidence,
                severity
        );
    }

    private static AutomatedPublicMuteRequest request(List<AutomatedModerationEvidence> evidence) {
        UUID trigger = evidence.getLast().moderationEventId();
        return new AutomatedPublicMuteRequest(
                TARGET_ID,
                "Player",
                trigger,
                "harassment",
                evidence.getLast().severity(),
                evidence.size(),
                Duration.ofDays(30),
                "rosechat-ai:" + trigger,
                evidence
        );
    }
}
