package net.enthusia.staff.paper.aireview;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Run only inside the viewer's owning entity callback; never disclose a queued roster after revocation. */
final class AiReviewQueueDelivery {
    private AiReviewQueueDelivery() {
    }

    /** Called on the viewer owner, so duty and queue permission are read at delivery time. */
    static boolean queueAuthorized(Player viewer, BooleanSupplier activeDuty) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(activeDuty, "activeDuty");
        return activeDuty.getAsBoolean() && AiReviewPermissions.queue(viewer);
    }

    /** Called by alert fanout on the recipient owner, never from the global poll. */
    static boolean notificationAuthorized(Player viewer, BooleanSupplier activeDuty,
            String notificationPermission) {
        Objects.requireNonNull(notificationPermission, "notificationPermission");
        return queueAuthorized(viewer, activeDuty)
                && viewer.hasPermission(notificationPermission);
    }
    static boolean deliverIfAuthorized(Player viewer, BooleanSupplier authorized,
            Runnable delivery, Consumer<RuntimeException> onFailure) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(authorized, "authorized");
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(onFailure, "onFailure");
        try {
            if (!viewer.isOnline() || !authorized.getAsBoolean()) {
                return false;
            }
            delivery.run();
            return true;
        } catch (RuntimeException exception) {
            onFailure.accept(exception);
            return false;
        }
    }
}
