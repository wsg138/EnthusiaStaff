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
    private static final String NOTIFICATION_PERMISSION = "enthusiastaff.ai-review.notify";
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
    void queuedListUsesCurrentDutyAndQueuePermissionAtDelivery() {
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicBoolean duty = new AtomicBoolean(true);
        AtomicBoolean queuePermission = new AtomicBoolean(true);
        Player viewer = permissionedViewer(online, queuePermission, new AtomicBoolean(true));
        AtomicInteger deliveries = new AtomicInteger();
        Runnable ownerDelivery = () -> AiReviewQueueDelivery.deliverIfAuthorized(
                viewer, () -> AiReviewQueueDelivery.queueAuthorized(viewer, duty::get),
                deliveries::incrementAndGet, failure -> fail("Unexpected authorization failure", failure));

        duty.set(false);
        ownerDelivery.run();
        assertEquals(0, deliveries.get(), "revoking active duty must block queued list output");

        duty.set(true);
        queuePermission.set(false);
        ownerDelivery.run();
        assertEquals(0, deliveries.get(), "revoking queue permission must block queued list output");

        queuePermission.set(true);
        ownerDelivery.run();
        assertEquals(1, deliveries.get(), "authorized active staff may receive queued output");

        online.set(false);
        ownerDelivery.run();
        assertEquals(1, deliveries.get(), "disconnected viewers must receive no further output");
    }

    @Test
    void notificationPredicateChecksCurrentDutyQueueAndNotificationPermission() {
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicBoolean duty = new AtomicBoolean(true);
        AtomicBoolean queuePermission = new AtomicBoolean(true);
        AtomicBoolean notificationPermission = new AtomicBoolean(true);
        Player viewer = permissionedViewer(online, queuePermission, notificationPermission);
        AtomicInteger notifications = new AtomicInteger();
        Runnable ownerDelivery = () -> AiReviewQueueDelivery.deliverIfAuthorized(
                viewer, () -> AiReviewQueueDelivery.notificationAuthorized(
                        viewer, duty::get, NOTIFICATION_PERMISSION),
                notifications::incrementAndGet,
                failure -> fail("Unexpected authorization failure", failure));

        notificationPermission.set(false);
        ownerDelivery.run();
        assertEquals(0, notifications.get(), "removed notification permission must suppress an alert");

        notificationPermission.set(true);
        queuePermission.set(false);
        ownerDelivery.run();
        assertEquals(0, notifications.get(), "removed queue permission must suppress an alert");

        queuePermission.set(true);
        duty.set(false);
        ownerDelivery.run();
        assertEquals(0, notifications.get(), "off-duty staff must not receive an alert");

        duty.set(true);
        ownerDelivery.run();
        assertEquals(1, notifications.get(), "fully authorized staff may receive one alert");

        online.set(false);
        ownerDelivery.run();
        assertEquals(1, notifications.get(), "a departed staff member must not receive another alert");
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

    private static Player permissionedViewer(
            AtomicBoolean online, AtomicBoolean queuePermission, AtomicBoolean notificationPermission) {
        return (Player) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> switch (method.getName()) {
                    case "isOnline" -> online.get();
                    case "hasPermission" -> {
                        String permission = (String) args[0];
                        if (AiReviewPermissions.QUEUE.equals(permission)) {
                            yield queuePermission.get();
                        }
                        if (NOTIFICATION_PERMISSION.equals(permission)) {
                            yield notificationPermission.get();
                        }
                        throw new AssertionError("Unexpected permission check: " + permission);
                    }
                    default -> throw new AssertionError("Unexpected player operation: " + method.getName());
                });
    }
    private static Player viewer(AtomicBoolean online) {
        return (Player) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> {
                    if (method.getName().equals("isOnline")) return online.get();
                    throw new AssertionError("Unexpected off-owner player access: " + method.getName());
                });
    }
}
