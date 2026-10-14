package net.enthusia.staff.domain.guided;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only proof of concept for a variable consequence, never an enforcement engine.
 * The sample configuration is injected by tests and is NOT an approved live policy.
 */
public final class GuidedRemedyPreview {
    public enum Action { WARNING, MUTE, NEEDS_REVIEW }

    public record Bounds(String candidateId, int warningCutoff, int maximumImpact,
                         Duration shortestMute, Duration longestMute, int historyWeight) {
        public Bounds {
            Objects.requireNonNull(candidateId);
            Objects.requireNonNull(shortestMute);
            Objects.requireNonNull(longestMute);
            if (!candidateId.startsWith("chat.spam.")
                    || warningCutoff < 1 || warningCutoff >= maximumImpact || maximumImpact > 100
                    || shortestMute.toMinutes() < 1 || longestMute.compareTo(shortestMute) < 0
                    || historyWeight < 0 || historyWeight > 20) {
                throw new IllegalArgumentException("Unapproved or invalid preview bounds");
            }
        }
    }

    public record Recommendation(Action type, Duration suggestedLength,
                                 Duration minimumLength, Duration maximumLength,
                                 int adjustedImpact, List<String> reasons) {
        public Recommendation {
            reasons = List.copyOf(reasons);
        }
    }

    private GuidedRemedyPreview() { }

    /**
     * The relatedHistoryCount must come from trusted case history and not from
     * arbitrary staff input. Only a bounded config can choose an action.
     */
    public static Recommendation calculate(
            GuidedChatInterview.Finding finding, Bounds config, int relatedHistoryCount
    ) {
        Objects.requireNonNull(finding);
        Objects.requireNonNull(config);
        if (relatedHistoryCount < 0) {
            throw new IllegalArgumentException("History count must not be negative");
        }
        if (finding.reviewRequired() || !config.candidateId().equals(finding.candidateId())) {
            return result(Action.NEEDS_REVIEW, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                    finding.impact(), List.of("Classification must be reviewed before recommending a sanction"));
        }
        int historyAdjustment = Math.min(100,
                (int) Math.min((long) relatedHistoryCount * config.historyWeight(), 100L));
        int impact = Math.min(100, finding.impact() + historyAdjustment);
        List<String> reasons = new ArrayList<>(finding.factors());
        if (historyAdjustment > 0) {
            reasons.add("Relevant prior incident contribution: " + historyAdjustment);
        }
        if (impact < config.warningCutoff()) {
            reasons.add("Within the configured warning band");
            return result(Action.WARNING, Duration.ZERO, Duration.ZERO, Duration.ZERO, impact, reasons);
        }

        long minimum = config.shortestMute().toMinutes();
        long maximum = config.longestMute().toMinutes();
        double ratio = Math.min(1.0, (double) (impact - config.warningCutoff())
                / (config.maximumImpact() - config.warningCutoff()));
        long proposed = Math.round(minimum + ratio * (maximum - minimum));
        // Staff may adjust within a deliberately narrow, calculated envelope.
        long lower = Math.max(minimum, Math.round(proposed * 0.75));
        long upper = Math.min(maximum, Math.max(proposed, Math.round(proposed * 1.25)));
        reasons.add("Duration scaled to the incident's impact and related history");
        return result(Action.MUTE, Duration.ofMinutes(proposed),
                Duration.ofMinutes(lower), Duration.ofMinutes(upper), impact, reasons);
    }

    private static Recommendation result(Action type, Duration suggested, Duration low,
                                         Duration high, int impact, List<String> reasons) {
        return new Recommendation(type, suggested, low, high, impact, reasons);
    }
}
