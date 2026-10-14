package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import net.enthusia.staff.domain.guided.GuidedChatInterview.Key;
import org.junit.jupiter.api.Test;

class GuidedRemedyPreviewTest {
    private static final GuidedRemedyPreview.Bounds SAMPLE_BOUNDS =
            new GuidedRemedyPreview.Bounds("chat.spam.low-level", 20, 85,
                    Duration.ofMinutes(15), Duration.ofHours(12), 8);

    private static GuidedChatInterview.Finding incident(String extent, String warningAnswer) {
        return GuidedChatInterview.start()
                .answer(Key.MEDIUM, "public-chat")
                .answer(Key.CONDUCT, "spam")
                .answer(Key.EXTENT, extent)
                .answer(Key.CONTINUED, warningAnswer)
                .finding();
    }

    @Test
    void noPriorWarningCanProduceAWarningNotAnAutomaticMute() {
        var recommendation = GuidedRemedyPreview.calculate(
                incident("few", "not-applicable"), SAMPLE_BOUNDS, 0);
        assertEquals(GuidedRemedyPreview.Action.WARNING, recommendation.type());
        assertEquals(Duration.ZERO, recommendation.suggestedLength());
        assertTrue(recommendation.reasons().stream()
                .anyMatch(reason -> reason.contains("warning band")));
    }

    @Test
    void ignoringAnActualWarningCanChangeTypeAndLengthWithinSameOffense() {
        var before = GuidedRemedyPreview.calculate(
                incident("few", "not-applicable"), SAMPLE_BOUNDS, 0);
        var after = GuidedRemedyPreview.calculate(
                incident("few", "yes"), SAMPLE_BOUNDS, 0);
        assertEquals(GuidedRemedyPreview.Action.WARNING, before.type());
        assertEquals(GuidedRemedyPreview.Action.MUTE, after.type());
        assertEquals(Duration.ofMinutes(15), after.suggestedLength());
    }

    @Test
    void RelevantHistoryAndIncidentSeverityChangeTheSuggestedLength() {
        var behavior = incident("few", "yes");
        var first = GuidedRemedyPreview.calculate(behavior, SAMPLE_BOUNDS, 0);
        var repeat = GuidedRemedyPreview.calculate(behavior, SAMPLE_BOUNDS, 4);
        assertEquals(GuidedRemedyPreview.Action.MUTE, repeat.type());
        assertTrue(repeat.suggestedLength().compareTo(first.suggestedLength()) > 0);
        assertTrue(repeat.suggestedLength().compareTo(repeat.minimumLength()) >= 0);
        assertTrue(repeat.suggestedLength().compareTo(repeat.maximumLength()) <= 0);
        assertTrue(repeat.maximumLength().compareTo(SAMPLE_BOUNDS.longestMute()) <= 0);
        assertTrue(repeat.reasons().stream().anyMatch(reason -> reason.contains("prior incident")));
    }

    @Test
    void SeriousOrUnmatchedConductRequiresReviewRatherThanInventingPunishment() {
        var slur = GuidedChatInterview.start()
                .answer(Key.MEDIUM, "public-chat")
                .answer(Key.CONDUCT, "slur")
                .answer(Key.TARGETING, "targeted").finding();
        var recommendation = GuidedRemedyPreview.calculate(slur, SAMPLE_BOUNDS, 0);
        assertEquals(GuidedRemedyPreview.Action.NEEDS_REVIEW, recommendation.type());
        assertEquals(Duration.ZERO, recommendation.suggestedLength());
    }

    @Test
    void CannotSetUnboundedPunishmentRangesOrUntrustedNegativeHistory() {
        assertThrows(IllegalArgumentException.class,
                () -> new GuidedRemedyPreview.Bounds("chat.spam.low-level", 20, 85,
                        Duration.ZERO, Duration.ofDays(30), 8));
        assertThrows(IllegalArgumentException.class,
                () -> new GuidedRemedyPreview.Bounds("safety.credible-threat", 20, 85,
                        Duration.ofMinutes(15), Duration.ofHours(12), 8));
        assertThrows(IllegalArgumentException.class,
                () -> GuidedRemedyPreview.calculate(incident("few", "yes"), SAMPLE_BOUNDS, -1));
    }
}
