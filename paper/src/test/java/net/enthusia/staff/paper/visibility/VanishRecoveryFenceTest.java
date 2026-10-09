package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VanishRecoveryFenceTest {
    private static final UUID PLAYER = UUID.fromString("12000000-0000-0000-0000-000000000001");

    @Test
    void completedExitStillRejectsThePreExitResult() {
        VanishRecoveryFence fence = new VanishRecoveryFence();
        var delayed = fence.begin(PLAYER).orElseThrow();
        fence.invalidate(PLAYER);
        assertFalse(fence.current(delayed));
    }

    @Test
    void reconnectCannotAuthorizeAReadFromThePreviousConnection() {
        VanishRecoveryFence fence = new VanishRecoveryFence();
        var previous = fence.begin(PLAYER).orElseThrow();
        fence.invalidate(PLAYER);
        var reconnect = fence.begin(PLAYER).orElseThrow();
        assertFalse(fence.current(previous));
        assertTrue(fence.current(reconnect));
    }

    @Test
    void oldCompletionCannotRemoveTheReconnectRead() {
        VanishRecoveryFence fence = new VanishRecoveryFence();
        var previous = fence.begin(PLAYER).orElseThrow();
        fence.invalidate(PLAYER);
        var reconnect = fence.begin(PLAYER).orElseThrow();
        fence.finish(previous);
        assertTrue(fence.current(reconnect));
        assertTrue(fence.begin(PLAYER).isEmpty());
    }

    @Test
    void currentCompletionAllowsAnotherReadAndKeepsOtherPlayersIndependent() {
        VanishRecoveryFence fence = new VanishRecoveryFence();
        var current = fence.begin(PLAYER).orElseThrow();
        var other = fence.begin(UUID.fromString("12000000-0000-0000-0000-000000000002")).orElseThrow();
        assertTrue(fence.begin(PLAYER).isEmpty());
        fence.finish(current);
        assertFalse(fence.current(current));
        assertTrue(fence.begin(PLAYER).isPresent());
        assertTrue(fence.current(other));
    }

    @Test
    void shutdownInvalidatesAllOutstandingReads() {
        VanishRecoveryFence fence = new VanishRecoveryFence();
        var pending = fence.begin(PLAYER).orElseThrow();
        fence.clear();
        assertFalse(fence.current(pending));
        assertTrue(fence.begin(PLAYER).isPresent());
    }
}
