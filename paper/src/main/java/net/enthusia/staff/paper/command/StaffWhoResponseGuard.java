package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.bukkit.entity.Player;

/** Final authorization and visibility fence for asynchronous /staffwho responses. */
final class StaffWhoResponseGuard {
    private static final String PERMISSION = "enthusiastaff.staffwho";

    private StaffWhoResponseGuard() {
    }

    /** Must be invoked on the viewer's owning entity thread, never on the worker. */
    static void deliverIfAuthorized(Player viewer, Runnable delivery) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(delivery, "delivery");
        boolean authorized;
        try {
            authorized = viewer.isOnline() && viewer.hasPermission(PERMISSION);
        } catch (RuntimeException unavailable) {
            return; // Fail closed if live permission state cannot be checked.
        }
        if (authorized) {
            delivery.run();
        }
    }

    /** Recheck current vanish visibility after all delayed snapshots and database work. */
    static <T> List<T> filterVisible(
            UUID viewerId,
            List<T> captured,
            Function<T, UUID> targetId,
            BiPredicate<UUID, UUID> canSee
    ) {
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(canSee, "canSee");
        if (viewerId == null) {
            return List.copyOf(captured); // Console is not a player viewer.
        }
        return captured.stream().filter(entry -> {
            try {
                return canSee.test(viewerId, targetId.apply(entry));
            } catch (RuntimeException unavailable) {
                return false;
            }
        }).toList();
    }
}
