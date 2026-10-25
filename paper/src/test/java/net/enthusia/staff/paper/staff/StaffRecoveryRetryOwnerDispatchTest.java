package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Worker/global timer must never read a Player's permissions or invoke recovery directly. */
class StaffRecoveryRetryOwnerDispatchTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-2222-3333-444444444444");

    @Test
    void delayedRetryWaitsForPlayerOwningThreadBeforeRecovering() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object ticket = new Object();
        pending.put(PLAYER_ID, ticket);
        AtomicReference<Consumer<Player>> entityCallback = new AtomicReference<>();
        AtomicInteger recoveries = new AtomicInteger();
        Player player = player();
        StaffRecoveryRetryOwnerDispatcher.forward(
                PLAYER_ID, ticket, pending,
                (id, action, retired) -> {
                    assertEquals(PLAYER_ID, id);
                    entityCallback.set(action);
                },
                current -> {
                    assertSame(player, current);
                    recoveries.incrementAndGet();
                });
        assertEquals(0, recoveries.get(), "Global timer must not invoke Player recovery");
        assertTrue(pending.containsKey(PLAYER_ID), "Keep retry pending until entity owner runs it");
        entityCallback.get().accept(player);
        assertEquals(1, recoveries.get());
        assertFalse(pending.containsKey(PLAYER_ID));
    }

    @Test
    void retiredOrDisconnectedEntityCannotTriggerRecoveryAndReleasesRetry() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object ticket = new Object();
        pending.put(PLAYER_ID, ticket);
        AtomicReference<Runnable> retiredCallback = new AtomicReference<>();
        AtomicInteger recoveries = new AtomicInteger();
        StaffRecoveryRetryOwnerDispatcher.forward(
                PLAYER_ID, ticket, pending,
                (id, action, retired) -> retiredCallback.set(retired),
                player -> recoveries.incrementAndGet());
        retiredCallback.get().run();
        assertEquals(0, recoveries.get());
        assertFalse(pending.containsKey(PLAYER_ID));
    }

    @Test
    void supersededOldRetryDoesNotConsumeOrExecuteTheNewOne() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object old = new Object();
        Object replacement = new Object();
        pending.put(PLAYER_ID, old);
        AtomicReference<Consumer<Player>> oldCallback = new AtomicReference<>();
        AtomicInteger recoveries = new AtomicInteger();
        StaffRecoveryRetryOwnerDispatcher.forward(
                PLAYER_ID, old, pending,
                (id, action, retired) -> oldCallback.set(action),
                player -> recoveries.incrementAndGet());
        pending.remove(PLAYER_ID, old);
        pending.put(PLAYER_ID, replacement);
        oldCallback.get().accept(player());
        assertEquals(0, recoveries.get(), "Old retry must not trigger after a rejoin or new attempt");
        assertSame(replacement, pending.get(PLAYER_ID));
    }

    @Test
    void unavailableEntityDispatcherDoesNotStrandTheRecoveryRetry() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object ticket = new Object();
        pending.put(PLAYER_ID, ticket);
        AtomicInteger recoveries = new AtomicInteger();

        assertThrows(IllegalStateException.class, () ->
                StaffRecoveryRetryOwnerDispatcher.forward(
                        PLAYER_ID, ticket, pending,
                        (id, action, retired) -> {
                            throw new IllegalStateException("entity scheduler unavailable");
                        },
                        player -> recoveries.incrementAndGet()));
        assertFalse(pending.containsKey(PLAYER_ID),
                "Scheduler rejection must not permanently block recovery for this player");
        assertEquals(0, recoveries.get());
    }

    @Test
    void staleDispatcherRejectionDoesNotEraseReplacementRetry() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object old = new Object();
        Object replacement = new Object();
        pending.put(PLAYER_ID, old);
        assertThrows(IllegalStateException.class, () ->
                StaffRecoveryRetryOwnerDispatcher.forward(
                        PLAYER_ID, old, pending,
                        (id, action, retired) -> {
                            pending.put(PLAYER_ID, replacement);
                            throw new IllegalStateException("previous task rejected");
                        },
                        player -> fail("Stale retry must not execute")));
        assertSame(replacement, pending.get(PLAYER_ID),
                "Rejected old dispatcher must not consume the current pending retry");
    }

    @Test
    void sourceTimerNeverRecoversPlayerOnGlobalRegionThread() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"));
        String retry = source.substring(
                source.indexOf("private void scheduleRecoveryRetry("),
                source.indexOf("private boolean staleFromPriorRuntime("));
        assertTrue(retry.contains("StaffRecoveryRetryOwnerDispatcher.forward("));
        assertTrue(retry.contains("this::onEntity"));
        assertTrue(retry.contains("this::recover"));
        assertFalse(retry.contains("recover(player)"),
                "recover(Player) must only run after the player owning scheduler handoff");
        assertFalse(retry.contains("getServer().getPlayer(playerId)"),
                "Avoid reading the Player or permissions from the global timer callback");
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> { throw new AssertionError(method.getName()); });
    }
}
