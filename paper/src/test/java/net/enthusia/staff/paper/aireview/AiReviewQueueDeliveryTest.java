package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class AiReviewQueueDeliveryTest {
    @Test
    void authorizedQueueSnapshotDeliversOnlyWhenOwnerCallbackRuns() {
        AtomicBoolean online = new AtomicBoolean(true);
        Player viewer = viewer(online);
        AtomicBoolean permission = new AtomicBoolean(true);
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger checks = new AtomicInteger();
        Runnable queuedOwnerCallback = () -> AiReviewQueueDelivery.deliverIfAuthorized(
                viewer, () -> { checks.incrementAndGet(); return permission.get(); },
                delivered::incrementAndGet, error -> fail("Unexpected failure", error));
        assertEquals(0, checks.get());
        assertEquals(0, delivered.get());
        queuedOwnerCallback.run();
        assertEquals(1, checks.get());
        assertEquals(1, delivered.get());
    }

    @Test
    void permissionRevokedWhileWaitingSuppressesSensitiveQueue() {
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicBoolean permission = new AtomicBoolean(true);
        AtomicInteger delivered = new AtomicInteger();
        Player viewer = viewer(online);
        Runnable queuedOwnerCallback = () -> AiReviewQueueDelivery.deliverIfAuthorized(
                viewer, permission::get, delivered::incrementAndGet,
                error -> fail("Unexpected failure", error));
        permission.set(false);
        queuedOwnerCallback.run();
        assertEquals(0, delivered.get());
    }

    @Test
    void departedViewerNeverReceivesQueueOrReadsPermission() {
        AtomicBoolean online = new AtomicBoolean(false);
        AtomicInteger checked = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        AiReviewQueueDelivery.deliverIfAuthorized(viewer(online),
                () -> { checked.incrementAndGet(); return true; },
                delivered::incrementAndGet, error -> fail("Unexpected failure", error));
        assertEquals(0, checked.get());
        assertEquals(0, delivered.get());
    }

    @Test
    void permissionLookupFailureFailsClosed() {
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AiReviewQueueDelivery.deliverIfAuthorized(viewer(new AtomicBoolean(true)),
                () -> { throw new IllegalStateException("permission backend unavailable"); },
                delivered::incrementAndGet, error -> failures.incrementAndGet());
        assertEquals(0, delivered.get());
        assertEquals(1, failures.get());
    }

    @Test
    void queueCommandAndNotificationsUseOwnerSafeDispatch() throws Exception {
        String command = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/aireview/AiReviewCommand.java"));
        String subsystem = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/aireview/AiReviewSubsystem.java"));
        assertTrue(command.contains("onPlayer(player, () -> AiReviewQueueDelivery.deliverIfAuthorized("));
        assertTrue(subsystem.contains("StaffAlertOwnerDispatch.alert("));
        String notifications = subsystem.substring(subsystem.indexOf("private void notifyNewItems("),
                subsystem.indexOf("@EventHandler", subsystem.indexOf("private void notifyNewItems(")));
        assertFalse(notifications.contains("if (!activeDuty(player)"));
        assertFalse(notifications.contains("for (Player player : plugin.getServer().getOnlinePlayers())"));
    }

    private static Player viewer(AtomicBoolean online) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> {
                    if (method.getName().equals("isOnline")) return online.get();
                    throw new AssertionError("Unexpected off-owner player access: " + method.getName());
                });
    }
}
