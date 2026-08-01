package net.enthusia.staff.paper.alert;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

interface PunishmentRequestAlertRuntime {
    List<PunishmentRequestAlertRecipient> onlineRecipients(int limit);

    Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId);

    boolean present(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertPresentation presentation
    );

    AutoCloseable registerJoinListener(Consumer<UUID> listener);

    Cancellable scheduleSynchronousRepeating(Runnable action, Duration initialDelay, Duration interval);

    Cancellable scheduleSynchronousDelayed(Runnable action, Duration delay);

    Cancellable scheduleAsynchronousRepeating(Runnable action, Duration initialDelay, Duration interval);

    void executeSynchronously(Runnable action);

    Logger logger();

    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }
}
