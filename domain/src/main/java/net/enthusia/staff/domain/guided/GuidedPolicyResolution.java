package net.enthusia.staff.domain.guided;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Calculated outcomes are bounded by real, configured policy steps and evaluated history. */
public final class GuidedPolicyResolution {
    private static final int SINGLE_SANCTION_COUNT = 1;
    public record Outcome(List<SanctionSpec> sanctions, String reasoning, boolean customDuration) {
        public Outcome { sanctions = List.copyOf(sanctions); }
    }

    private GuidedPolicyResolution() { }

    public static Outcome calculate(GuidedIncidentWizard.Finding finding,
                                    PunishmentAssessment assessed, int preference) {
        return calculate(finding, assessed, preference, GuidedSeverityProfile.prototype());
    }

    /** The profile must match the immutable interview version used to produce the finding. */
    public static Outcome calculate(GuidedIncidentWizard.Finding finding,
                                    PunishmentAssessment assessed, int preference,
                                    GuidedSeverityProfile profile) {
        validate(finding, assessed, preference, profile);
        List<PunishmentStep> ladder = assessed.policy().steps();
        int historyStep = assessed.escalation().selectedStep().ordinal();
        int selected = selectedStep(finding.preliminaryImpact(), historyStep, ladder.size(), profile);
        List<SanctionSpec> originals = ladder.get(selected).sanctions();
        checkTransition(ladder, historyStep, selected);
        requireOrdinaryGuidedRemedy(originals);
        String explanation = explanation(finding, historyStep, selected);
        return variableDuration(originals, ladder, selected, preference, explanation,
                profile, finding.preliminaryImpact());
    }

    private static void validate(GuidedIncidentWizard.Finding finding,
                                 PunishmentAssessment assessed, int preference,
                                 GuidedSeverityProfile profile) {
        Objects.requireNonNull(finding);
        Objects.requireNonNull(assessed);
        Objects.requireNonNull(profile);
        if (finding.needsReview() || !assessed.policy().id().equals(finding.candidateId())
                || !profile.equals(finding.severityProfile())
                || preference < -1 || preference > 1) {
            throw new IllegalArgumentException("This incident requires individual review");
        }
    }

    private static int selectedStep(int impact, int historyStep, int ladderSize,
                                    GuidedSeverityProfile profile) {
        return Math.min(ladderSize - 1, historyStep + profile.stepOffset(impact));
    }

    private static void checkTransition(List<PunishmentStep> ladder, int baselineStep,
                                        int selected) {
        if (selected <= baselineStep) {
            return;
        }
        List<SanctionSpec> previous = ladder.get(baselineStep).sanctions();
        // A severity jump must not escape an unsupported baseline remedy.
        requireOrdinaryGuidedRemedy(previous);
        for (int step = baselineStep + 1; step <= selected; step++) {
            List<SanctionSpec> next = ladder.get(step).sanctions();
            // A large incident score must not jump over a restricted policy step.
            requireOrdinaryGuidedRemedy(next);
            if (!safeContextualTransition(previous, next)) {
                throw new IllegalArgumentException(
                        "Incident severity would cross a restricted sanction tier; manual review required");
            }
            previous = next;
        }
    }

    /**
     * A broad factual questionnaire must never bypass the specialized review
     * and approval path for bans, permanent outcomes, or nonstandard remedies,
     * including when prior history already selected such a policy step.
     */
    private static void requireOrdinaryGuidedRemedy(List<SanctionSpec> sanctions) {
        if (sanctions.size() != SINGLE_SANCTION_COUNT) {
            throw new IllegalArgumentException("Mixed sanctions require explicit review");
        }
        SanctionSpec spec = sanctions.getFirst();
        boolean warning = spec.type() == SanctionType.WARNING && spec.length().isInstant();
        boolean temporaryMute = spec.type() == SanctionType.MUTE
                && spec.length().temporary().isPresent();
        if (!warning && !temporaryMute) {
            throw new IllegalArgumentException(
                    "This sanction type or duration requires explicit staff review");
        }
    }

    private static String explanation(GuidedIncidentWizard.Finding finding,
                                      int historyStep, int selected) {
        return "Guided v1: " + finding.conduct() + " in " + finding.medium()
                + "; severityProfile=" + finding.severityProfile().version()
                + "; impact=" + finding.preliminaryImpact()
                + "; historyStep=" + historyStep + "; selectedStep=" + selected
                + "; factors=" + String.join(", ", finding.facts());
    }

    private static Outcome variableDuration(List<SanctionSpec> originals,
                                            List<PunishmentStep> ladder, int selected,
                                            int preference, String explanation,
                                            GuidedSeverityProfile profile, int incidentImpact) {
        if (originals.size() != 1 || originals.getFirst().length().temporary().isEmpty()) {
            return new Outcome(originals, explanation + "; configured remedy", false);
        }
        SanctionSpec initial = originals.getFirst();
        Duration low = initial.length().temporary().orElseThrow();
        Duration high = nextDuration(ladder, selected, initial);
        if (high.compareTo(low) <= 0) {
            return new Outcome(originals, explanation + "; maximum policy tier", false);
        }
        return calculateWithinBounds(originals, initial, low, high, preference, explanation,
                profile, incidentImpact);
    }

    private static Outcome calculateWithinBounds(List<SanctionSpec> originals,
                                                  SanctionSpec initial, Duration low,
                                                  Duration high, int preference,
                                                  String explanation, GuidedSeverityProfile profile,
                                                  int incidentImpact) {
        // Anchor minute-granularity choices to the exact configured lower bound.
        // Truncating low.toMinutes() can otherwise produce a sub-policy sanction
        // when a configured duration includes seconds.
        long availableMinutes = high.minus(low).toMinutes();
        double ratio = profile.durationFraction(preference, incidentImpact);
        Duration calculated = low.plus(Duration.ofMinutes(Math.round(availableMinutes * ratio)));
        if (calculated.compareTo(high) > 0) {
            calculated = high;
        }
        if (low.equals(calculated)) {
            return new Outcome(originals, explanation, false);
        }
        String durationLabel = calculated.equals(Duration.ofMinutes(calculated.toMinutes()))
                ? calculated.toMinutes() + "m" : calculated.toString();
        return new Outcome(List.of(new SanctionSpec(initial.type(),
                SanctionLength.temporary(calculated))),
                explanation + "; boundedDuration=" + durationLabel, true);
    }

    /**
     * Generic incident scores must not invent a ban, a permanent punishment,
     * or a mixed/confiscation remedy. Those need an explicit reviewed route.
     * A policy-sanctioned warning-to-mute progression is permitted.
     */
    private static boolean safeContextualTransition(List<SanctionSpec> baseline,
                                                    List<SanctionSpec> candidate) {
        if (baseline.size() != 1 || candidate.size() != 1) {
            return false;
        }
        SanctionSpec before = baseline.getFirst();
        SanctionSpec after = candidate.getFirst();
        if (after.length().isPermanent() || before.length().isPermanent()) {
            return false;
        }
        if (after.type() == before.type()) {
            if (after.type() == SanctionType.MUTE) {
                var current = before.length().temporary();
                var next = after.length().temporary();
                // Misordered custom ladders must not turn increased severity into a shorter mute.
                return current.isPresent() && next.isPresent()
                        && next.orElseThrow().compareTo(current.orElseThrow()) >= 0;
            }
            return true;
        }
        return before.type() == SanctionType.WARNING && after.type() == SanctionType.MUTE;
    }

    private static Duration nextDuration(List<PunishmentStep> steps, int selected,
                                         SanctionSpec initial) {
        Duration current = initial.length().temporary().orElseThrow();
        for (int i = selected + 1; i < steps.size(); i++) {
            List<SanctionSpec> items = steps.get(i).sanctions();
            // Never interpolate across an intervening sanction class.
            if (items.size() != 1 || items.getFirst().type() != initial.type()
                    || items.getFirst().length().temporary().isEmpty()) {
                break;
            }
            Duration candidate = items.getFirst().length().temporary().orElseThrow();
            if (candidate.compareTo(current) > 0) {
                return candidate;
            }
        }
        return current;
    }
}
