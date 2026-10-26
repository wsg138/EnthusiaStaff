package net.enthusia.staff.paper.automod;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class AutomodOwnerAlertDispatchTest {
    @Test
    void permissionAndDeliveryRunOnlyOnTheRecipientsOwningEntityThread() {
        Player recipient = player();
        AtomicReference<Runnable> ownerCallback = new AtomicReference<>();
        AtomicInteger permissionChecks = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        AutomodOwnerAlertDispatch.dispatch(
                List.of(recipient),
                (target, callback) -> {
                    assertSame(recipient, target);
                    ownerCallback.set(callback);
                    return true;
                },
                target -> {
                    permissionChecks.incrementAndGet();
                    return true;
                },
                target -> delivered.incrementAndGet(),
                failure -> fail("Unexpected scheduling error", failure)
        );
        assertEquals(0, permissionChecks.get(), "Global-region enumeration must not check player permissions");
        assertEquals(0, delivered.get(), "Global-region enumeration must not send player messages");
        ownerCallback.get().run();
        assertEquals(1, permissionChecks.get());
        assertEquals(1, delivered.get());
    }

    @Test
    void revokedPermissionAtDeliveryTimePreventsStaleStaffAlert() {
        Player recipient = player();
        AtomicReference<Runnable> ownerCallback = new AtomicReference<>();
        AtomicBoolean permitted = new AtomicBoolean(true);
        AtomicInteger delivered = new AtomicInteger();
        AutomodOwnerAlertDispatch.dispatch(
                List.of(recipient),
                (target, callback) -> { ownerCallback.set(callback); return true; },
                target -> permitted.get(),
                target -> delivered.incrementAndGet(),
                failure -> fail("Unexpected scheduling error", failure)
        );
        permitted.set(false);
        ownerCallback.get().run();
        assertEquals(0, delivered.get());
    }

    @Test
    void retiredRecipientCannotPreventOtherStaffAlerts() {
        Player retired = player();
        Player active = player();
        AtomicInteger delivered = new AtomicInteger();
        AutomodOwnerAlertDispatch.dispatch(
                List.of(retired, active),
                (target, callback) -> {
                    if (target == retired) return false;
                    callback.run();
                    return true;
                },
                target -> true,
                target -> delivered.incrementAndGet(),
                failure -> fail("Unexpected scheduling error", failure)
        );
        assertEquals(1, delivered.get());
    }

    @Test
    void thrownEntitySchedulerFailureDoesNotBlockOtherStaffAlerts() {
        Player retired = player();
        Player active = player();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        AutomodOwnerAlertDispatch.dispatch(
                List.of(retired, active),
                (target, callback) -> {
                    if (target == retired) throw new IllegalStateException("region retired");
                    callback.run();
                    return true;
                },
                target -> true,
                target -> delivered.incrementAndGet(),
                failure -> failures.incrementAndGet()
        );
        assertEquals(1, failures.get());
        assertEquals(1, delivered.get());
    }

    @Test
    void automodRoutesPlayerMessagesThroughOwnerScheduler() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/automod/AutomodListener.java"));
        assertTrue(source.contains("AutomodOwnerAlertDispatch.dispatch("));
        assertTrue(source.contains("PlayerMessageDispatcher"));
        assertFalse(source.contains("sender.sendMessage("));
        assertFalse(source.contains(".filter(player -> player.hasPermission("),
                "Global-region iteration must not query live player permissions");
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> {
                    throw new AssertionError("Unexpected player access: " + method.getName());
                });
    }
}
