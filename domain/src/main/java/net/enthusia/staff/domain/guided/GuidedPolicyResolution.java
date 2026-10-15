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
        validate(finding, assessed, preference);
        List<PunishmentStep> ladder = assessed.policy().steps();
        int historyStep = assessed.escalation().selectedStep().ordinal();
        int selected = selectedStep(finding.preliminaryImpact(), historyStep, ladder.size());
        List<SanctionSpec> originals = ladder.get(selected).sanctions();
        checkTransition(ladder, historyStep, selected);
        requireOrdinaryGuidedRemedy(originals);
        String explanation = explanation(finding, historyStep, selected);
        return variableDuration(originals, ladder, selected, preference, explanation);
    }

    private static void validate(GuidedIncidentWizard.Finding finding,
                                 PunishmentAssessment assessed, int preference) {
        Objects.requireNonNull(finding);
        Objects.requireNonNull(assessed);
        if (finding.needsReview() || !assessed.policy().id().equals(finding.candidateId())
                || preference < -1 || preference > 1) {
            throw new IllegalArgumentException("This incident requires individual review");
        }
    }

    private static int selectedStep(int impact, int historyStep, int ladderSize) {
        int adjustment = impact >= 45 ? 2 : impact >= 25 ? 1 : 0;
        return Math.min(ladderSize - 1, historyStep + adjustment);
    }

    private static void checkTransition(List<PunishmentStep> ladder, int baselineStep,
                                        int selected) {
        if (selected <= baselineStep) {
            return;
        }
        List<SanctionSpec> previous = ladder.get(baselineStep).sanctions();
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
                + "; impact=" + finding.preliminaryImpact()
                + "; historyStep=" + historyStep + "; selectedStep=" + selected
                + "; factors=" + String.join(", ", finding.facts());
    }

    private static Outcome variableDuration(List<SanctionSpec> originals,
                                            List<PunishmentStep> ladder, int selected,
                                            int preference, String explanation) {
        if (originals.size() != 1 || originals.getFirst().length().temporary().isEmpty()) {
            return new Outcome(originals, explanation + "; configured remedy", false);
        }
        SanctionSpec initial = originals.getFirst();
        Duration low = initial.length().temporary().orElseThrow();
        Duration high = nextDuration(ladder, selected, initial);
        if (high.compareTo(low) <= 0) {
            return new Outcome(originals, explanation + "; maximum policy tier", false);
        }
        return calculateWithinBounds(originals, initial, low, high, preference, explanation);
    }

    private static Outcome calculateWithinBounds(List<SanctionSpec> originals,
                                                  SanctionSpec initial, Duration low,
                                                  Duration high, int preference,
                                                  String explanation) {
        long lowMinutes = low.toMinutes();
        long highMinutes = high.toMinutes();
        double ratio = switch (preference) {
            case -1 -> 0.25;
            case 1 -> 0.75;
            default -> 0.5;
        };
        long minutes = Math.max(lowMinutes, Math.min(highMinutes,
                lowMinutes + Math.round((highMinutes - lowMinutes) * ratio)));
        Duration calculated = Duration.ofMinutes(minutes);
        if (low.equals(calculated)) {
            return new Outcome(originals, explanation, false);
        }
        return new Outcome(List.of(new SanctionSpec(initial.type(),
                SanctionLength.temporary(calculated))),
                explanation + "; boundedDuration=" + minutes + "m", true);
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
