package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffWhoResponseGuardTest {
    private static final UUID VIEWER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID A = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID B = UUID.fromString("10000000-0000-0000-0000-000000000003");

    @Test
    void permissionIsCheckedAtFinalDeliveryNotAtCommandSubmission() {
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicBoolean permitted = new AtomicBoolean(true);
        AtomicInteger deliveries = new AtomicInteger();
        Player viewer = player(online, permitted, false);
        permitted.set(false);
        StaffWhoResponseGuard.deliverIfAuthorized(viewer, deliveries::incrementAndGet);
        assertEquals(0, deliveries.get());
        permitted.set(true);
        StaffWhoResponseGuard.deliverIfAuthorized(viewer, deliveries::incrementAndGet);
        assertEquals(1, deliveries.get());
    }

    @Test
    void disconnectedOrFailingPermissionLookupDoesNotDiscloseRoster() {
        AtomicBoolean online = new AtomicBoolean(false);
        AtomicBoolean permitted = new AtomicBoolean(true);
        AtomicInteger deliveries = new AtomicInteger();
        StaffWhoResponseGuard.deliverIfAuthorized(player(online, permitted, false), deliveries::incrementAndGet);
        assertEquals(0, deliveries.get());
        online.set(true);
        StaffWhoResponseGuard.deliverIfAuthorized(player(online, permitted, true), deliveries::incrementAndGet);
        assertEquals(0, deliveries.get());
    }

    @Test
    void newlyHiddenTargetsAreFilteredAtDeliveryTime() {
        record Target(UUID id) {}
        List<Target> captured = List.of(new Target(A), new Target(B));
        AtomicBoolean canSeeB = new AtomicBoolean(true);
        assertEquals(2, StaffWhoResponseGuard.filterVisible(VIEWER, captured, Target::id,
                (viewer, target) -> !target.equals(B) || canSeeB.get()).size());
        canSeeB.set(false);
        assertEquals(List.of(captured.get(0)), StaffWhoResponseGuard.filterVisible(VIEWER, captured,
                Target::id, (viewer, target) -> !target.equals(B) || canSeeB.get()));
        assertEquals(captured, StaffWhoResponseGuard.filterVisible(null, captured,
                Target::id, (viewer, target) -> false));
    }

    @Test
    void visibilityLookupFailureFailsClosedForThatTarget() {
        record Target(UUID id) {}
        List<Target> visible = StaffWhoResponseGuard.filterVisible(VIEWER,
                List.of(new Target(A), new Target(B)), Target::id, (viewer, target) -> {
                    if (target.equals(B)) throw new IllegalStateException("visibility unavailable");
                    return true;
                });
        assertEquals(1, visible.size());
        assertEquals(A, visible.get(0).id());
    }

    private static Player player(AtomicBoolean online, AtomicBoolean permitted, boolean throwPermission) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOnline" -> online.get();
                    case "hasPermission" -> {
                        assertEquals("enthusiastaff.staffwho", args[0]);
                        if (throwPermission) throw new IllegalStateException("permissions unavailable");
                        yield permitted.get();
                    }
                    default -> throw new AssertionError("Unexpected method: " + method.getName());
                });
    }
}
