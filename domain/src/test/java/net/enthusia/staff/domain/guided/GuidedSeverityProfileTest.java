package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class GuidedSeverityProfileTest {
    private static final String FLOOD = "spam.message-flooding";
    private static final String YES = "yes";
    private static final String INVALID_VERSION = "bad";

    private static GuidedSeverityProfile trial() {
        return new GuidedSeverityProfile(
                "calibration-test-v1", 10, 15, 30, 10, 15, 10,
                40, 60, 0.10, 0.55, 0.90, 0.0);
    }

    private static GuidedIncidentWizard interview(String warning) {
        return GuidedIncidentWizard.start("chat")
                .answer("medium", "public-chat")
                .answer("conduct", "spam")
                .answer("extent", "sustained")
                .answer("continued", warning);
    }

    private static PunishmentAssessment assessment() {
        return assessment(0);
    }

    private static PunishmentAssessment assessment(int historyOrdinal) {
        var warn = new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
        var mute15 = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofMinutes(15)));
        var mute60 = new SanctionSpec(SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofMinutes(60)));
        List<PunishmentStep> ladder = List.of(
                new PunishmentStep(0, "Warning", List.of(warn)),
                new PunishmentStep(1, "Short mute", List.of(mute15)),
                new PunishmentStep(2, "Longer mute", List.of(mute60)));
        var policy = new ReasonPolicy(FLOOD, "spam", "Flooding", 15, true, ladder);
        var history = new EscalationDecision(historyOrdinal, historyOrdinal, 0,
                List.of(), ladder.get(historyOrdinal));
        return new PunishmentAssessment("test-policy-v1", policy, history,
                ladder.get(historyOrdinal).sanctions());
    }

    @Test
    void prototypeKeepsTheHistoricalWeightsAndOutcome() {
        var prototype = GuidedSeverityProfile.prototype();
        var ordinary = interview("not-applicable").finding();
        var ignored = interview(YES).finding();
        assertEquals(25, ordinary.preliminaryImpact());
        assertEquals(35, ignored.preliminaryImpact());
        assertEquals(prototype, ignored.severityProfile());
        assertEquals(1, prototype.stepOffset(ignored.preliminaryImpact()));
        assertEquals(SanctionType.MUTE,
                GuidedPolicyResolution.calculate(ignored, assessment(), 0)
                        .sanctions().getFirst().type());
    }

    @Test
    void unapprovedTrialProfileCanBeComparedWithoutChangingProductionDefaults() {
        var trial = trial();
        var incident = interview(YES).finding(trial);
        assertEquals("calibration-test-v1", incident.severityProfile().version());
        assertEquals(SanctionType.WARNING,
                GuidedPolicyResolution.calculate(incident, assessment(), 0, trial)
                        .sanctions().getFirst().type());
        assertEquals(SanctionType.MUTE,
                GuidedPolicyResolution.calculate(interview(YES).finding(),
                        assessment(), 0).sanctions().getFirst().type());
    }

    @Test
    void trialDurationFractionsRemainBoundedByCurrentTemporaryMuteTier() {
        var profile = trial();
        var incident = interview(YES).finding(profile);
        var withHistory = assessment(1);
        Duration shorter = GuidedPolicyResolution.calculate(incident, withHistory, -1, profile)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration balanced = GuidedPolicyResolution.calculate(incident, withHistory, 0, profile)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration longer = GuidedPolicyResolution.calculate(incident, withHistory, 1, profile)
                .sanctions().getFirst().length().temporary().orElseThrow();
        assertEquals(Duration.ofMinutes(20), shorter);
        assertEquals(Duration.ofMinutes(40), balanced);
        assertEquals(Duration.ofMinutes(56), longer);
    }

    private static GuidedSeverityProfile impactTrial() {
        return new GuidedSeverityProfile(
                "impact-interpolation-trial-not-approved", 10, 15, 30, 10, 15, 10,
                25, 45, 0.25, 0.50, 0.75, 0.30);
    }

    @Test
    void sameTierIgnoredWarningMayAffectBoundedDurationOnlyWithOptInTrial() {
        var profile = impactTrial();
        var baseline = interview("not-applicable").finding(profile);
        var ignored = interview(YES).finding(profile);
        assertEquals(25, baseline.preliminaryImpact());
        assertEquals(35, ignored.preliminaryImpact());
        assertEquals(1, profile.stepOffset(baseline.preliminaryImpact()));
        assertEquals(1, profile.stepOffset(ignored.preliminaryImpact()));
        var baselineResult = GuidedPolicyResolution.calculate(baseline, assessment(), 0, profile);
        var ignoredResult = GuidedPolicyResolution.calculate(ignored, assessment(), 0, profile);
        assertEquals(SanctionType.MUTE, baselineResult.sanctions().getFirst().type());
        assertEquals(SanctionType.MUTE, ignoredResult.sanctions().getFirst().type());
        Duration withoutWarning = baselineResult.sanctions().getFirst().length()
                .temporary().orElseThrow();
        Duration withIgnoredWarning = ignoredResult.sanctions().getFirst().length()
                .temporary().orElseThrow();
        assertTrue(withIgnoredWarning.compareTo(withoutWarning) > 0);
        assertTrue(withoutWarning.compareTo(Duration.ofMinutes(15)) >= 0);
        assertTrue(withIgnoredWarning.compareTo(Duration.ofMinutes(60)) <= 0);

        var baselinePrototype = interview("not-applicable").finding();
        var ignoredPrototype = interview(YES).finding();
        assertEquals(GuidedPolicyResolution.calculate(baselinePrototype, assessment(), 0)
                        .sanctions(),
                GuidedPolicyResolution.calculate(ignoredPrototype, assessment(), 0).sanctions());
    }

    @Test
    void optInTrialStillPreservesStaffPreferenceAndSafetyBounds() {
        var profile = impactTrial();
        var incident = interview(YES).finding(profile);
        Duration lower = GuidedPolicyResolution.calculate(incident, assessment(), -1, profile)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration middle = GuidedPolicyResolution.calculate(incident, assessment(), 0, profile)
                .sanctions().getFirst().length().temporary().orElseThrow();
        Duration upper = GuidedPolicyResolution.calculate(incident, assessment(), 1, profile)
                .sanctions().getFirst().length().temporary().orElseThrow();
        assertTrue(lower.compareTo(middle) < 0);
        assertTrue(middle.compareTo(upper) < 0);
        assertTrue(lower.compareTo(Duration.ofMinutes(15)) >= 0);
        assertTrue(upper.compareTo(Duration.ofMinutes(60)) <= 0);
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 15, 30, 10, 15, 10,
                25, 45, 0.25, 0.5, 0.75, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 15, 30, 10, 15, 10,
                25, 45, 0.25, 0.5, 0.75, 0.51));
        assertEquals(0.50, GuidedSeverityProfile.prototype().durationFraction(0, 25));
    }

    @Test
    void changedOrWrongProfileIsRejectedEvenIfVersionWasReused() {
        var incident = interview(YES).finding(trial());
        var changed = new GuidedSeverityProfile("calibration-test-v1",
                10, 15, 30, 11, 15, 10, 40, 60, 0.10, 0.55, 0.90, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident, assessment(), 0, changed));
        assertThrows(IllegalArgumentException.class,
                () -> GuidedPolicyResolution.calculate(incident, assessment(), 0));
    }

    @Test
    void malformedWeightsThresholdsAndFractionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, -1, 15, 30, 10, 15, 10, 25, 45, 0.25, 0.5, 0.75, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 15, 30, 10, 15, 10, 45, 25, 0.25, 0.5, 0.75, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 30, 15, 10, 15, 10, 25, 45, 0.25, 0.5, 0.75, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 15, 30, 10, 15, 10, 10, 45, 0.25, 0.5, 0.75, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 15, 30, 10, 15, 10, 25, 45, 0.80, 0.5, 0.75, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GuidedSeverityProfile(
                INVALID_VERSION, 10, 15, 30, 10, 15, 10, 25, 45, 0.25, Double.NaN, 0.75, 0.0));
        assertThrows(IllegalArgumentException.class, () -> trial().durationFraction(3));
        assertTrue(trial().durationFraction(-1) < trial().durationFraction(1));
    }
}
