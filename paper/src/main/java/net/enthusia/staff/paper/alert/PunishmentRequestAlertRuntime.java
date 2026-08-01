package net.enthusia.staff.paper.alert;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

interface PunishmentRequestAlertRuntime {
    List<PunishmentRequestAlertRecipient> onlineRecipients(int limit);

    default Optional<PunishmentRequestAlertRecipient> snapshotRecipient(UUID playerId) {
        return currentRecipient(playerId);
    }

    Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId);

    boolean present(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertPresentation presentation
    );

    AutoCloseable registerJoinListener(Consumer<UUID> listener);

    Cancellable scheduleSynchronousRepeating(Runnable action, Duration initialDelay, Duration interval);

    Cancellable scheduleSynchronousDelayed(Runnable action, Duration delay);

    Cancellable scheduleAsynchronousRepeating(Runnable action, Duration initialDelay, Duration interval);

    default boolean executeForRecipient(
            UUID playerId,
            Runnable action,
            Runnable retired
    ) {
        executeSynchronously(action);
        return true;
    }

    void executeSynchronously(Runnable action);

    Logger logger();

    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }
}
