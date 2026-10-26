package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PunishmentCommitOwnerDispatchTest {
    @Test
    void liveOnlineCheckAndPunishmentRunOnlyOnTargetOwner() {
        Player player = player();
        UUID id = UUID.randomUUID();
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger effects = new AtomicInteger();
        PunishmentCommitOwnerDispatch.forward(id,
                requested -> { assertEquals(id, requested); return player; },
                (recipient, callback) -> { assertSame(player, recipient); owner.set(callback); return true; },
                recipient -> { checks.incrementAndGet(); return true; },
                recipient -> effects.incrementAndGet(),
                error -> fail("Unexpected failure", error));
        assertNotNull(owner.get());
        assertEquals(0, checks.get());
        assertEquals(0, effects.get());
        owner.get().run();
        assertEquals(1, checks.get());
        assertEquals(1, effects.get());
    }

    @Test
    void departedPlayerCannotReceiveDelayedWarningOrKick() {
        Player player = player();
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicInteger effects = new AtomicInteger();
        PunishmentCommitOwnerDispatch.forward(UUID.randomUUID(), id -> player,
                (recipient, callback) -> { owner.set(callback); return true; },
                recipient -> online.get(), recipient -> effects.incrementAndGet(),
                error -> fail("Unexpected failure", error));
        online.set(false);
        owner.get().run();
        assertEquals(0, effects.get());
    }

    @Test
    void missingTargetDoesNotScheduleAndLookupFailureIsReported() {
        AtomicInteger schedules = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        PunishmentCommitOwnerDispatch.forward(UUID.randomUUID(), id -> null,
                (recipient, callback) -> { schedules.incrementAndGet(); return true; },
                recipient -> true, recipient -> fail("No target"),
                error -> failures.incrementAndGet());
        PunishmentCommitOwnerDispatch.forward(UUID.randomUUID(), id -> {
                    throw new IllegalStateException("lookup unavailable");
                }, (recipient, callback) -> { schedules.incrementAndGet(); return true; },
                recipient -> true, recipient -> fail("No target"),
                error -> failures.incrementAndGet());
        assertEquals(0, schedules.get());
        assertEquals(1, failures.get());
    }

    @Test
    void rejectedOrThrowingOwnerSchedulerDoesNotRunPunishment() {
        Player player = player();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger effects = new AtomicInteger();
        PunishmentCommitOwnerDispatch.forward(UUID.randomUUID(), id -> player,
                (recipient, callback) -> false,
                recipient -> true, recipient -> effects.incrementAndGet(),
                error -> failures.incrementAndGet());
        PunishmentCommitOwnerDispatch.forward(UUID.randomUUID(), id -> player,
                (recipient, callback) -> { throw new IllegalStateException("region retired"); },
                recipient -> true, recipient -> effects.incrementAndGet(),
                error -> failures.incrementAndGet());
        assertEquals(2, failures.get());
        assertEquals(0, effects.get());
    }

    @Test
    void ownerCallbackFailureIsLoggedWithoutRetryingOrCrossThreadFallback() {
        Player player = player();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        PunishmentCommitOwnerDispatch.forward(UUID.randomUUID(), id -> player,
                (recipient, callback) -> { callback.run(); return true; },
                recipient -> true,
                recipient -> { attempts.incrementAndGet(); throw new IllegalStateException("kick unavailable"); },
                error -> failures.incrementAndGet());
        assertEquals(1, attempts.get());
        assertEquals(1, failures.get());
    }

    @Test
    void productionCommitPathDefersOnlineCheckAndEffectToEntityOwner() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/enforcement/PaperPunishmentCommitEffects.java"));
        assertTrue(source.contains("PunishmentCommitOwnerDispatch.forward("));
        assertFalse(source.contains("player == null || !player.isOnline()"),
                "The global-region callback must not inspect live player online state");
        assertTrue(source.contains("player -> !closed && player.isOnline()"));
        assertTrue(source.contains("player.getScheduler().execute("));
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> {
                    throw new AssertionError("Unexpected off-owner player access: " + method.getName());
                });
    }
}
