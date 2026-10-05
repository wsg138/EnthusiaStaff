package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.enthusia.staff.paper.visibility.SilentContainerTracker.BlockKey;
import org.junit.jupiter.api.Test;

class SilentContainerTrackerTest {
    private static final UUID VANISHED = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISIBLE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORLD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final BlockKey CHEST = new BlockKey(WORLD, 10, 64, 10);
    private static final BlockKey BARREL = new BlockKey(WORLD, 11, 64, 10);
    private static final Predicate<UUID> VANISHED_CHECK = VANISHED::equals;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneId.of("UTC"));
    private static final long NOW = CLOCK.millis();

    private static SilentContainerTracker tracker() {
        return new SilentContainerTracker(VANISHED_CHECK, CLOCK);
    }

    @Test
    void vanishedOpenerSuppressesAnimationForOthers() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VANISHED, List.of(CHEST), NOW);

        assertTrue(tracker.shouldSuppressFor(CHEST, VISIBLE, NOW));
    }

    @Test
    void vanishedViewerStillSeesOwnAnimation() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VANISHED, List.of(CHEST), NOW);

        assertFalse(tracker.shouldSuppressFor(CHEST, VANISHED, NOW));
    }

    @Test
    void visibleOpenerDoesNotSuppress() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VISIBLE, List.of(CHEST), NOW);

        assertFalse(tracker.shouldSuppressFor(CHEST, VISIBLE, NOW));
        assertFalse(tracker.shouldSuppressFor(CHEST, VANISHED, NOW));
    }

    @Test
    void mixedViewersDoNotSuppress() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VANISHED, List.of(CHEST), NOW);
        tracker.onOpen(VISIBLE, List.of(CHEST), NOW);

        // A visible player is viewing: animations must play for everyone.
        assertFalse(tracker.shouldSuppressFor(CHEST, UUID.randomUUID(), NOW));
    }

    @Test
    void vanishedCloseKeepsCloseAnimationSilentBriefly() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VANISHED, List.of(CHEST), NOW);
        tracker.onClose(VANISHED, List.of(CHEST), NOW);

        assertTrue(tracker.shouldSuppressFor(
                CHEST, VISIBLE, NOW + SilentContainerTracker.CLOSE_SUPPRESSION_MILLIS - 1));
        assertFalse(tracker.shouldSuppressFor(
                CHEST, VISIBLE, NOW + SilentContainerTracker.CLOSE_SUPPRESSION_MILLIS + 1));
    }

    @Test
    void visibleCloserDoesNotSuppressCloseAnimation() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VISIBLE, List.of(CHEST), NOW);
        tracker.onClose(VISIBLE, List.of(CHEST), NOW);

        assertFalse(tracker.shouldSuppressFor(CHEST, VISIBLE, NOW + 1));
    }

    @Test
    void untrackedPositionNeverSuppresses() {
        SilentContainerTracker tracker = tracker();

        assertFalse(tracker.shouldSuppressFor(BARREL, VISIBLE, NOW));
    }

    @Test
    void positionsAreIndependent() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VANISHED, List.of(CHEST), NOW);

        assertTrue(tracker.shouldSuppressFor(CHEST, VISIBLE, NOW));
        assertFalse(tracker.shouldSuppressFor(BARREL, VISIBLE, NOW));
    }

    @Test
    void doubleChestBothHalvesSuppressed() {
        SilentContainerTracker tracker = tracker();
        BlockKey left = new BlockKey(WORLD, 10, 64, 10);
        BlockKey right = new BlockKey(WORLD, 11, 64, 10);
        tracker.onOpen(VANISHED, List.of(left, right), NOW);

        assertTrue(tracker.shouldSuppressFor(left, VISIBLE, NOW));
        assertTrue(tracker.shouldSuppressFor(right, VISIBLE, NOW));
    }

    @Test
    void visibleViewerJoiningClearsStaleSuppression() {
        SilentContainerTracker tracker = tracker();
        tracker.onOpen(VANISHED, List.of(CHEST), NOW);
        tracker.onClose(VANISHED, List.of(CHEST), NOW);
        // Close animation still silent...
        assertTrue(tracker.shouldSuppressFor(CHEST, VISIBLE, NOW + 100));
        // ...until a visible player opens it, then animations play.
        tracker.onOpen(VISIBLE, List.of(CHEST), NOW + 200);

        assertFalse(tracker.shouldSuppressFor(CHEST, VISIBLE, NOW + 300));
    }
}
