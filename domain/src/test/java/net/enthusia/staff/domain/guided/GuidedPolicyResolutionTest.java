package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class GuidedPolicyResolutionTest {
    private static final String SPAM_FAMILY = "spam";
    private static final String FLOOD_ID = "spam.message-flooding";
    private static SanctionSpec warn() {
        return new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
    }
    private static SanctionSpec mute(long minutes) {
        return new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofMinutes(minutes)));
    }
    private static final List<PunishmentStep> STEPS = List.of(
            new PunishmentStep(0, "Warning", List.of(warn())),
            new PunishmentStep(1, "Second warning", List.of(warn())),
            new PunishmentStep(2, "Short mute", List.of(mute(15))),
            new PunishmentStep(3, "Longer mute", List.of(mute(360))),
            new PunishmentStep(4, "Day mute", List.of(mute(1440)))
    );

    private static PunishmentAssessment assessment(int historyOrdinal, String id) {
        var policy = new ReasonPolicy(id, SPAM_FAMILY, "Spam", 10, true, STEPS);
        var escalation = new EscalationDecision(historyOrdinal, historyOrdinal, 0,
                List.of(), STEPS.get(historyOrdinal));
        return new PunishmentAssessment("fixture.1", policy, escalation,
                STEPS.get(historyOrdinal).sanctions());
    }

    private static GuidedIncidentWizard.Finding finding(String extent, String warning) {
        return GuidedIncidentWizard.start("chat")
                .answer("medium", "public-chat").answer("conduct", SPAM_FAMILY)
                .answer("extent", extent).answer("continued", warning).finding();
    }

    @Test
    void noWarningMeansNoDefaultPenalty() {
        var outcome = GuidedPolicyResolution.calculate(finding("few", "not-applicable"),
                assessment(0, "spam.low-level"), 0);
        assertEquals(List.of(warn()), outcome.sanctions());
        assertFalse(outcome.customDuration());
    }

    @Test
    void variableLengthRemainsBetweenConfiguredBounds() {
        var incident = finding("large-scale", "yes");
        var a = assessment(0, FLOOD_ID);
        Duration low = GuidedPolicyResolution.calculate(incident, a, -1)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration mid = GuidedPolicyResolution.calculate(incident, a, 0)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration high = GuidedPolicyResolution.calculate(incident, a, 1)
                .sanctions().getFirst().length().temporary().orElseThrow();
        assertTrue(low.compareTo(mid) < 0);
        assertTrue(mid.compareTo(high) < 0);
        assertTrue(low.compareTo(Duration.ofMinutes(15)) >= 0);
        assertTrue(high.compareTo(Duration.ofHours(6)) <= 0);
    }

    @Test
    void evaluatedCaseHistoryChangesDurationWithoutNewStaffQuestion() {
        var incident = finding("large-scale", "yes");
        var first = GuidedPolicyResolution.calculate(incident,
                assessment(0, FLOOD_ID), 0);
        var repeated = GuidedPolicyResolution.calculate(incident,
                assessment(1, FLOOD_ID), 0);
        Duration a = first.sanctions().getFirst().length().temporary().orElseThrow();
        Duration b = repeated.sanctions().getFirst().length().temporary().orElseThrow();
        assertTrue(b.compareTo(a) > 0);
    }

    private static PunishmentAssessment withFinalSanction(SanctionSpec finalSanction) {
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Baseline warning", List.of(warn())),
                new PunishmentStep(1, "Elevated consequence", List.of(finalSanction)));
        ReasonPolicy policy = new ReasonPolicy(FLOOD_ID, SPAM_FAMILY,
                "Flooding", 15, true, steps);
        EscalationDecision decision = new EscalationDecision(0, 0, 0, List.of(), steps.getFirst());
        return new PunishmentAssessment("fixture.1", policy, decision, List.of(warn()));
    }

    @Test
    void boundedDurationCannotTruncateBelowSecondPrecisionPolicyMinimum() {
        Duration minimum = Duration.ofSeconds(90);
        Duration nextTier = Duration.ofSeconds(100);
        SanctionSpec lower = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(minimum));
        SanctionSpec upper = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(nextTier));
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Warning", List.of(warn())),
                new PunishmentStep(1, "Short mute", List.of(lower)),
                new PunishmentStep(2, "Longer mute", List.of(upper)));
        ReasonPolicy policy = new ReasonPolicy("spam.low-level", SPAM_FAMILY,
                "Spam", 10, true, steps);
        EscalationDecision history = new EscalationDecision(
                1, 1, 0, List.of(), steps.get(1));
        PunishmentAssessment assessed = new PunishmentAssessment(
                "fixture.second-precision", policy, history, List.of(lower));
        var incident = finding("few", "not-applicable");
        for (int preference : new int[]{-1, 0, 1}) {
            Duration result = GuidedPolicyResolution.calculate(incident, assessed, preference)
                    .sanctions().getFirst().length().temporary().orElseThrow();
            assertTrue(result.compareTo(minimum) >= 0,
                    "Punishment must not fall below the exact configured minimum");
            assertTrue(result.compareTo(nextTier) <= 0,
                    "Punishment must not exceed the next same-type tier");
        }
    }

    @Test
    void severityNeverAutomaticallyEscalatesToBanOrPermanentConsequence() {
        var incident = finding("large-scale", "yes");
        SanctionSpec temporaryBan = new SanctionSpec(SanctionType.BAN,
                SanctionLength.temporary(Duration.ofDays(7)));
        SanctionSpec permanentMute = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.permanent());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident,
                        withFinalSanction(temporaryBan), 0));
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident,
                        withFinalSanction(permanentMute), 0));
    }

    @Test
    void alreadyEscalatedBanOrPermanentPolicyRequiresExplicitReview() {
        var finding = finding("few", "not-applicable");
        var ban = new SanctionSpec(SanctionType.BAN,
                SanctionLength.temporary(Duration.ofDays(7)));
        var permanentMute = new SanctionSpec(SanctionType.MUTE, SanctionLength.permanent());
        assertEscalatedRestrictedSanctionRequiresReview(finding, ban);
        assertEscalatedRestrictedSanctionRequiresReview(finding, permanentMute);
    }

    private static void assertEscalatedRestrictedSanctionRequiresReview(
            GuidedIncidentWizard.Finding finding, SanctionSpec restricted) {
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "First", List.of(warn())),
                new PunishmentStep(1, "Restricted", List.of(restricted)));
        ReasonPolicy policy = new ReasonPolicy("spam.low-level", SPAM_FAMILY,
                "Minor spam", 10, true, steps);
        EscalationDecision prior = new EscalationDecision(1, 1, 0, List.of(), steps.get(1));
        PunishmentAssessment assessed = new PunishmentAssessment(
                "test.version", policy, prior, steps.get(1).sanctions());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(finding, assessed, 0));
    }

    @Test
    void severityCannotSkipRestrictedIntermediatePolicyTier() {
        var incident = finding("large-scale", "yes");
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Warning", List.of(warn())),
                new PunishmentStep(1, "Requires ban review", List.of(
                        new SanctionSpec(SanctionType.BAN,
                                SanctionLength.temporary(Duration.ofDays(1))))),
                new PunishmentStep(2, "Temporary mute", List.of(mute(60))));
        ReasonPolicy policy = new ReasonPolicy(FLOOD_ID, SPAM_FAMILY,
                "Flooding", 15, true, steps);
        EscalationDecision prior = new EscalationDecision(
                0, 0, 0, List.of(), steps.getFirst());
        PunishmentAssessment assessed = new PunishmentAssessment(
                "fixture.restricted-middle", policy, prior, List.of(warn()));

        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident, assessed, 0));
    }

    @Test
    void unsafeOrMismatchedOffenseIsRejected() {
        var spam = finding("few", "not-applicable");
        assertThrows(IllegalArgumentException.class, () -> GuidedPolicyResolution.calculate(
                spam, assessment(0, FLOOD_ID), 0));
        assertThrows(IllegalArgumentException.class, () -> GuidedPolicyResolution.calculate(
                spam, assessment(0, "spam.low-level"), 2));
    }

    @Test
    void voiceNoiseCannotBeAutoConvertedToAMinecraftMute() {
        var voice = GuidedIncidentWizard.start("chat")
                .answer("medium", "voice").answer("conduct", "spam")
                .answer("extent", "sustained").answer("continued", "not-applicable")
                .finding();
        assertEquals("spam.noise-pollution", voice.candidateId());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(
                        voice, assessment(0, "spam.noise-pollution"), 0));
    }

    @Test
    void descendingMuteLadderNeedsManualReviewInsteadOfSeverityDowngrade() {
        var incident = finding("large-scale", "yes");
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Warning", List.of(warn())),
                new PunishmentStep(1, "One hour", List.of(mute(60))),
                new PunishmentStep(2, "Misordered fifteen minutes", List.of(mute(15))));
        var policy = new ReasonPolicy(FLOOD_ID, SPAM_FAMILY, "Flooding", 15, true, steps);
        var history = new EscalationDecision(0, 0, 0, List.of(), steps.getFirst());
        var assessed = new PunishmentAssessment("test.descending", policy, history,
                steps.getFirst().sanctions());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident, assessed, 0));
    }

    @Test
    void malformedFutureMuteTierCannotBeSkippedDuringDurationInterpolation() {
        var incident = finding("few", "not-applicable");
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Warning", List.of(warn())),
                new PunishmentStep(1, "One hour", List.of(mute(60))),
                new PunishmentStep(2, "Misordered fifteen minutes", List.of(mute(15))),
                new PunishmentStep(3, "Three hours", List.of(mute(180))));
        var policy = new ReasonPolicy("spam.low-level", SPAM_FAMILY,
                "Spam", 10, true, steps);
        var history = new EscalationDecision(1, 1, 0, List.of(), steps.get(1));
        var assessed = new PunishmentAssessment("test.future-descending", policy,
                history, steps.get(1).sanctions());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident, assessed, 0),
                "Do not interpolate one-hour mutes toward three hours across a shorter intervening tier");
    }

    @Test
    void severityCannotJumpFromPermanentMuteBaselineToOrdinaryMute() {
        var incident = finding("large-scale", "yes");
        var restricted = new SanctionSpec(SanctionType.MUTE, SanctionLength.permanent());
        List<PunishmentStep> steps = List.of(
                new PunishmentStep(0, "Permanent mute", List.of(restricted)),
                new PunishmentStep(1, "Mute", List.of(mute(15))),
                new PunishmentStep(2, "Longer mute", List.of(mute(60))));
        var policy = new ReasonPolicy(FLOOD_ID, SPAM_FAMILY, "Flooding", 15, true, steps);
        var history = new EscalationDecision(0, 0, 0, List.of(), steps.getFirst());
        var assessed = new PunishmentAssessment("test.permanent-baseline", policy, history,
                steps.getFirst().sanctions());
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident, assessed, 0));
    }
}
