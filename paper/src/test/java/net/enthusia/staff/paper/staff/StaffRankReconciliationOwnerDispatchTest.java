package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffRankReconciliationOwnerDispatchTest {
    private static final UUID ID = UUID.fromString("abcdefab-0000-1111-2222-abcdefabcdef");

    @Test
    void rankCheckRunsOnlyOnOwnerAndHoldsSingleFlightUntilCompletion() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object ticket = new Object();
        pending.put(ID, ticket);
        AtomicReference<Runnable> callback = new AtomicReference<>();
        AtomicInteger checks = new AtomicInteger();
        StaffRankReconciliationOwnerDispatcher.forward(ID, ticket, pending,
                (id, action, retired) -> callback.set(() -> action.accept(player())),
                player -> checks.incrementAndGet());
        assertEquals(0, checks.get(), "Global timer must never read live rank");
        assertSame(ticket, pending.get(ID), "Pending check stays single-flight while queued");
        callback.get().run();
        assertEquals(1, checks.get());
        assertFalse(pending.containsKey(ID));
        callback.get().run();
        assertEquals(1, checks.get(), "Duplicate callback must not repeat a rank mutation");
    }

    @Test
    void oldQueuedCallbackCannotTouchNewPlayerOrConsumeNewCheckAfterQuitRejoin() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object old = new Object();
        Object current = new Object();
        pending.put(ID, old);
        AtomicReference<Runnable> oldCallback = new AtomicReference<>();
        AtomicInteger checks = new AtomicInteger();
        StaffRankReconciliationOwnerDispatcher.forward(ID, old, pending,
                (id, action, retired) -> oldCallback.set(() -> action.accept(player())),
                player -> checks.incrementAndGet());
        assertTrue(pending.remove(ID, old), "Quit cancels the old identity");
        assertNull(pending.putIfAbsent(ID, current), "Rejoin can queue a fresh check");
        oldCallback.get().run();
        assertEquals(0, checks.get(), "Old check must not reconcile the new session");
        assertSame(current, pending.get(ID), "Old callback must not remove new token");
    }

    @Test
    void retiredCallbackOnlyClearsItsOwnTicket() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object old = new Object();
        Object newer = new Object();
        pending.put(ID, old);
        AtomicReference<Runnable> retired = new AtomicReference<>();
        StaffRankReconciliationOwnerDispatcher.forward(ID, old, pending,
                (id, action, onRetired) -> retired.set(onRetired),
                player -> fail("Retired task must not reconcile"));
        pending.put(ID, newer);
        retired.get().run();
        assertSame(newer, pending.get(ID));
    }

    @Test
    void synchronousOwnerSchedulingFailureReleasesOldTicketWithoutErasingReplacement() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        Object old = new Object();
        Object newer = new Object();
        pending.put(ID, old);
        assertThrows(IllegalStateException.class, () ->
                StaffRankReconciliationOwnerDispatcher.forward(ID, old, pending,
                        (id, action, retired) -> {
                            pending.put(ID, newer);
                            throw new IllegalStateException("global scheduler unavailable");
                        }, player -> fail("Rejected task must not reconcile")));
        assertSame(newer, pending.get(ID));
        assertThrows(IllegalStateException.class, () ->
                StaffRankReconciliationOwnerDispatcher.forward(ID, newer, pending,
                        (id, action, retired) -> {
                            throw new IllegalStateException("global scheduler unavailable");
                        }, player -> fail("Rejected task must not reconcile")));
        assertFalse(pending.containsKey(ID));
    }

    @Test
    void managerUsesTokenGuardForRankChecksAndClearsOnQuit() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"));
        assertTrue(source.contains("ConcurrentMap<UUID, Object> pendingRankChecks"));
        assertTrue(source.contains("pendingRankChecks.putIfAbsent(playerId, ticket)"));
        assertTrue(source.contains("StaffRankReconciliationOwnerDispatcher.forward("));
        assertTrue(source.contains("Staff rank owner check could not be scheduled for "),
                "A rejected player must not cancel the global periodic rank sweep");
        assertTrue(source.contains("pendingRankChecks.remove(playerId);"));
        assertFalse(source.contains("pendingRankChecks.add(playerId)"));
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (self, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return ID;
                    throw new AssertionError("Unexpected Player access: " + method.getName());
                });
    }
}
