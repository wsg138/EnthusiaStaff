package net.enthusia.staff.paper.alert;

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

class StaffAlertOwnerDispatchTest {
    @Test
    void permissionAndDeliveryWaitForOwningPlayerCallback() {
        Player player = player();
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger messages = new AtomicInteger();
        StaffAlertOwnerDispatch.dispatch(
                List.of(player),
                (recipient, callback) -> { assertSame(player, recipient); owner.set(callback); return true; },
                recipient -> { checks.incrementAndGet(); return true; },
                recipient -> messages.incrementAndGet(),
                error -> fail("Unexpected failure", error));
        assertNotNull(owner.get());
        assertEquals(0, checks.get());
        assertEquals(0, messages.get());
        owner.get().run();
        assertEquals(1, checks.get());
        assertEquals(1, messages.get());
    }

    @Test
    void revokedPermissionAfterQueueingDropsMessage() {
        Player player = player();
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicBoolean permitted = new AtomicBoolean(true);
        AtomicInteger messages = new AtomicInteger();
        StaffAlertOwnerDispatch.dispatch(
                List.of(player),
                (recipient, callback) -> { owner.set(callback); return true; },
                recipient -> permitted.get(),
                recipient -> messages.incrementAndGet(),
                error -> fail("Unexpected failure", error));
        permitted.set(false);
        owner.get().run();
        assertEquals(0, messages.get());
    }

    @Test
    void disconnectedRecipientAtDeliveryTimeIsRejected() {
        Player player = player();
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicInteger messages = new AtomicInteger();
        StaffAlertOwnerDispatch.dispatch(
                List.of(player),
                (recipient, callback) -> { owner.set(callback); return true; },
                recipient -> online.get(),
                recipient -> messages.incrementAndGet(),
                error -> fail("Unexpected failure", error));
        online.set(false);
        owner.get().run();
        assertEquals(0, messages.get());
    }

    @Test
    void rejectedAndThrowingSchedulersDoNotBlockOtherRecipients() {
        Player rejected = player();
        Player throwing = player();
        Player active = player();
        AtomicInteger messages = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        StaffAlertOwnerDispatch.dispatch(
                List.of(rejected, throwing, active),
                (recipient, callback) -> {
                    if (recipient == rejected) return false;
                    if (recipient == throwing) throw new IllegalStateException("region retired");
                    callback.run();
                    return true;
                },
                recipient -> true,
                recipient -> messages.incrementAndGet(),
                error -> failures.incrementAndGet());
        assertEquals(1, messages.get());
        assertEquals(1, failures.get());
    }

    @Test
    void failingRecipientCallbackDoesNotSuppressOtherStaff() {
        Player failing = player();
        Player active = player();
        AtomicInteger messages = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        StaffAlertOwnerDispatch.dispatch(
                List.of(failing, active),
                (recipient, callback) -> { callback.run(); return true; },
                recipient -> true,
                recipient -> {
                    if (recipient == failing) throw new IllegalStateException("delivery rejected");
                    messages.incrementAndGet();
                },
                error -> failures.incrementAndGet());
        assertEquals(1, failures.get());
        assertEquals(1, messages.get());
    }

    @Test
    void economyConfiscationAndAuthorityAlertsUseSharedOwnerFanout() throws Exception {
        String root = "src/main/java/net/enthusia/staff/paper/";
        for (String file : List.of("economy/EconomyCoordinator.java",
                "inventory/ConfiscationCoordinator.java",
                "inventory/InventoryCoordinator.java",
                "auth/DiscordStaffAuthorityEndpoint.java")) {
            String source = Files.readString(Path.of(root + file));
            assertTrue(source.contains("StaffAlertOwnerDispatch.alert("), file);
            assertFalse(source.contains(".filter(player -> player.hasPermission(\"enthusiastaff.alerts\"))"), file);
            assertFalse(source.contains(".filter(player -> PaperStaffRankResolver.resolve(player::hasPermission)"), file);
        }
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (self, method, args) -> {
                    throw new AssertionError("Unexpected off-owner player access: " + method.getName());
                });
    }
}
