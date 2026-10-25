package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentGuiHistoryFenceTest {
    private static final UUID VIEWER = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("31000000-0000-0000-0000-000000000002");

    @Test
    void newerHistoryNavigationInvalidatesOlderOutOfOrderWorkerResult() {
        PunishmentGuiHistoryFence fence = new PunishmentGuiHistoryFence();
        Object first = fence.begin(VIEWER);
        Object second = fence.begin(VIEWER);
        assertFalse(fence.consume(VIEWER, first));
        assertTrue(fence.consume(VIEWER, second));
        assertFalse(fence.consume(VIEWER, second));
    }

    @Test
    void closingOrSwitchingAwayDiscardsPendingHistory() {
        PunishmentGuiHistoryFence fence = new PunishmentGuiHistoryFence();
        Object pending = fence.begin(VIEWER);
        fence.invalidate(VIEWER);
        assertFalse(fence.consume(VIEWER, pending));
        assertTrue(fence.consume(VIEWER, fence.begin(VIEWER)));
    }

    @Test
    void oneViewersCloseCannotCancelAnotherViewersHistory() {
        PunishmentGuiHistoryFence fence = new PunishmentGuiHistoryFence();
        Object one = fence.begin(VIEWER);
        Object two = fence.begin(OTHER);
        fence.invalidate(VIEWER);
        assertFalse(fence.consume(VIEWER, one));
        assertTrue(fence.consume(OTHER, two));
    }
}
