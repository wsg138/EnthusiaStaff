package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffDutyOwnerReconcilerTest {
    private static final UUID ID = UUID.fromString("12345678-1111-2222-3333-444444444444");

    @Test
    void globalScanOnlyQueuesOwnerCallbackBeforeReadingPermissionOrSignaling() {
        var reconciler = new StaffDutyOwnerReconciler();
        Player player = player();
        AtomicReference<Runnable> callback = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger signals = new AtomicInteger();
        reconciler.reconcile(List.of(player), (target, task, retired) -> {
            callback.set(task);
            return true;
        }, target -> { reads.incrementAndGet(); return true; },
                target -> signals.incrementAndGet(), failure -> fail(failure));
        assertEquals(0, reads.get());
        assertEquals(0, signals.get());
        callback.get().run();
        assertEquals(1, reads.get());
        assertEquals(1, signals.get());
    }

    @Test
    void permissionRevocationSignalsAgainWithoutSpammingUnchangedState() {
        var reconciler = new StaffDutyOwnerReconciler();
        Player player = player();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger signals = new AtomicInteger();
        StaffDutyOwnerReconciler.OwnerScheduler immediate = (p, task, retired) -> {
            task.run();
            return true;
        };
        for (int i = 0; i < 2; i++) {
            reconciler.reconcile(List.of(player), immediate, p -> active.get(),
                    p -> signals.incrementAndGet(), failure -> fail(failure));
        }
        assertEquals(1, signals.get());
        active.set(false);
        reconciler.reconcile(List.of(player), immediate, p -> active.get(),
                p -> signals.incrementAndGet(), failure -> fail(failure));
        assertEquals(2, signals.get());
    }

    @Test
    void retiredAndThrowingSchedulerCanRetryOnNextTick() {
        var reconciler = new StaffDutyOwnerReconciler();
        Player player = player();
        AtomicInteger signals = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        reconciler.reconcile(List.of(player), (p, task, retired) -> false,
                p -> true, p -> signals.incrementAndGet(), failure -> failures.incrementAndGet());
        reconciler.reconcile(List.of(player), (p, task, retired) -> {
            throw new IllegalStateException("region retiring");
        }, p -> true, p -> signals.incrementAndGet(), failure -> failures.incrementAndGet());
        reconciler.reconcile(List.of(player), (p, task, retired) -> {
            retired.run();
            return true;
        }, p -> true, p -> signals.incrementAndGet(), failure -> failures.incrementAndGet());
        reconciler.reconcile(List.of(player), (p, task, retired) -> {
            task.run();
            return true;
        }, p -> true, p -> signals.incrementAndGet(), failure -> failures.incrementAndGet());
        assertEquals(1, signals.get());
        assertEquals(1, failures.get());
    }

    @Test
    void staleCallbackAfterQuitAndRejoinCannotSignalOrSuppressNewPlayer() {
        var reconciler = new StaffDutyOwnerReconciler();
        Player oldPlayer = player();
        Player newPlayer = player();
        AtomicReference<Runnable> oldCallback = new AtomicReference<>();
        AtomicInteger oldReads = new AtomicInteger();
        AtomicInteger signals = new AtomicInteger();
        reconciler.reconcile(List.of(oldPlayer), (p, task, retired) -> {
            oldCallback.set(task);
            return true;
        }, p -> { oldReads.incrementAndGet(); return true; },
                p -> signals.incrementAndGet(), failure -> fail(failure));
        reconciler.forget(oldPlayer);
        reconciler.reconcile(List.of(newPlayer), (p, task, retired) -> {
            task.run();
            return true;
        }, p -> true, p -> signals.incrementAndGet(), failure -> fail(failure));
        oldCallback.get().run();
        assertEquals(0, oldReads.get());
        assertEquals(1, signals.get());
    }

    @Test
    void closeCancelsQueuedWorkAndFailedSignalRetries() {
        var reconciler = new StaffDutyOwnerReconciler();
        Player player = player();
        AtomicInteger signals = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        StaffDutyOwnerReconciler.OwnerScheduler immediate = (p, task, retired) -> {
            task.run();
            return true;
        };
        for (int i = 0; i < 2; i++) {
            reconciler.reconcile(List.of(player), immediate, p -> true, p -> {
                if (signals.incrementAndGet() == 1) throw new IllegalStateException("signal failed");
            }, failure -> failures.incrementAndGet());
        }
        assertEquals(2, signals.get());
        assertEquals(1, failures.get());
        AtomicReference<Runnable> pending = new AtomicReference<>();
        reconciler.reconcile(List.of(player), (p, task, retired) -> {
            pending.set(task);
            return true;
        }, p -> fail("Closed context must not read permissions"), p -> fail("Closed context must not signal"),
                failure -> fail(failure));
        reconciler.close();
        pending.get().run();
        reconciler.reconcile(List.of(player), (p, task, retired) -> fail("Closed context must not schedule"),
                p -> true, p -> fail("Closed context must not signal"), failure -> fail(failure));
    }

    @Test
    void productionWiringNeverReadsPermissionOrSignalsFromGlobalCallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/auth/LuckPermsStaffDutyContext.java"));
        assertTrue(source.contains("StaffDutyOwnerReconciler"));
        assertTrue(source.contains("player.getScheduler().execute("));
        assertTrue(source.contains("reconciler.reconcile("));
        assertTrue(source.contains("reconciler.forget("));
        String globalCallback = source.substring(source.indexOf("private void reconcileOnlinePlayers()"),
                source.indexOf("@EventHandler(priority = EventPriority.MONITOR)"));
        assertFalse(globalCallback.contains("staffMode.isUnrestricted(player)"),
                "Live permission access must only occur in the owner-thread callback");
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (self, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return ID;
                    if (method.getName().equals("isOnline")) return true;
                    throw new AssertionError("Unexpected Player access: " + method.getName());
                });
    }
}
