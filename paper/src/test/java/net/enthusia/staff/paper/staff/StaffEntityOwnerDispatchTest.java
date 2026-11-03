package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffEntityOwnerDispatchTest {
    private static final UUID ID = UUID.fromString("11111111-1111-2222-3333-444444444444");

    @Test
    void entitySchedulerThrowRetiresPendingTaskInsteadOfStrandingIt() {
        Player player = player();
        AtomicInteger retired = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        StaffEntityOwnerDispatch.forward(ID, id -> player,
                (current, operation, onRetire) -> {
                    throw new IllegalStateException("entity scheduler is shutting down");
                },
                current -> fail("Must not execute operation on global region"),
                retired::incrementAndGet,
                failure -> failures.incrementAndGet());
        assertEquals(1, retired.get());
        assertEquals(1, failures.get());
    }

    @Test
    void playerLookupThrowRetiresPendingTaskInsteadOfStrandingIt() {
        AtomicInteger retired = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        StaffEntityOwnerDispatch.forward(ID, id -> {
                    throw new IllegalStateException("player lookup unavailable");
                },
                (current, operation, onRetire) -> fail("Must not schedule"),
                current -> fail("Must not run operation"),
                retired::incrementAndGet,
                failure -> failures.incrementAndGet());
        assertEquals(1, retired.get());
        assertEquals(1, failures.get());
    }

    @Test
    void unavailablePlayerAndDeclinedSchedulerRetireTask() {
        AtomicInteger retired = new AtomicInteger();
        StaffEntityOwnerDispatch.forward(ID, id -> null,
                (current, operation, onRetire) -> fail("No player to schedule"),
                current -> fail("No player to run"),
                retired::incrementAndGet,
                failure -> fail("Missing player is not an exception"));
        assertEquals(1, retired.get());

        StaffEntityOwnerDispatch.forward(ID, id -> player(),
                (current, operation, onRetire) -> false,
                current -> fail("Declined scheduler must not run operation"),
                retired::incrementAndGet,
                failure -> fail("Declined scheduler is not an exception"));
        assertEquals(2, retired.get());
    }

    @Test
    void acceptedSchedulerDefersOperationToEntityCallback() {
        Player player = player();
        AtomicInteger operations = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        final Runnable[] callback = new Runnable[1];
        StaffEntityOwnerDispatch.forward(ID, id -> player,
                (current, operation, onRetire) -> {
                    assertSame(player, current);
                    callback[0] = operation;
                    return true;
                },
                current -> {
                    assertSame(player, current);
                    operations.incrementAndGet();
                },
                retired::incrementAndGet,
                failure -> fail("Successful submission must not fail"));
        assertEquals(0, operations.get(), "Global callback must not run Player operation");
        assertEquals(0, retired.get());
        assertNotNull(callback[0]);
        callback[0].run();
        assertEquals(1, operations.get());
        assertEquals(0, retired.get());
    }

    @Test
    void productionOnEntityUsesFailureAwareDispatcher() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"));
        String implementation = source.substring(source.indexOf("private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired)"));
        assertTrue(implementation.contains("StaffEntityOwnerDispatch.forward("));
        assertTrue(implementation.contains("player.getScheduler().execute("));
        assertTrue(implementation.contains("retired,"));
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> { throw new AssertionError(method.getName()); });
    }
}
