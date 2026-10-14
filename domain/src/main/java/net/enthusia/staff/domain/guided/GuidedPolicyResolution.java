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
    public record Outcome(List<SanctionSpec> sanctions, String reasoning, boolean customDuration) {
        public Outcome { sanctions = List.copyOf(sanctions); }
    }

    private GuidedPolicyResolution() { }

    public static Outcome calculate(GuidedIncidentWizard.Finding finding,
                                    PunishmentAssessment assessed, int preference) {
        Objects.requireNonNull(finding);
        Objects.requireNonNull(assessed);
        if (finding.needsReview() || !assessed.policy().id().equals(finding.candidateId())
                || preference < -1 || preference > 1) {
            throw new IllegalArgumentException("This incident requires individual review");
        }
        List<PunishmentStep> ladder = assessed.policy().steps();
        int historyStep = assessed.escalation().selectedStep().ordinal();
        int adjustment = finding.preliminaryImpact() >= 45 ? 2
                : finding.preliminaryImpact() >= 25 ? 1 : 0;
        int selected = Math.min(ladder.size() - 1, historyStep + adjustment);
        List<SanctionSpec> originals = ladder.get(selected).sanctions();
        if (selected > historyStep && !safeContextualTransition(
                ladder.get(historyStep).sanctions(), originals)) {
            throw new IllegalArgumentException(
                    "Incident severity would change the sanction class or permanence; manual review required");
        }
        String explanation = "Guided v1: " + finding.conduct() + " in " + finding.medium()
                + "; impact=" + finding.preliminaryImpact()
                + "; historyStep=" + historyStep + "; selectedStep=" + selected
                + "; factors=" + String.join(", ", finding.facts());
        if (originals.size() != 1 || originals.getFirst().length().temporary().isEmpty()) {
            return new Outcome(originals, explanation + "; configured remedy", false);
        }
        SanctionSpec initial = originals.getFirst();
        Duration low = initial.length().temporary().orElseThrow();
        Duration high = nextDuration(ladder, selected, initial);
        if (high.compareTo(low) <= 0) {
            return new Outcome(originals, explanation + "; maximum policy tier", false);
        }
        long lowMinutes = low.toMinutes(), highMinutes = high.toMinutes();
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
            if (items.size() != 1 || items.getFirst().type() != initial.type()
                    || items.getFirst().length().temporary().isEmpty()) {
                continue;
            }
            Duration candidate = items.getFirst().length().temporary().orElseThrow();
            if (candidate.compareTo(current) > 0) {
                return candidate;
            }
        }
        return current;
    }
}
