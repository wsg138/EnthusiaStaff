package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;

/**
 * Publishes staff-mode state only after the live player mutation completes and hands failures to the
 * bounded persistence executor without performing JDBC work on the player's entity scheduler.
 */
final class StaffModeActivationCoordinator {
    enum ActivationPath {
        INITIAL_ENTRY("initial entry"),
        ACTIVE_RECOVERY("active recovery");

        private final String description;

        ActivationPath(String description) {
            this.description = description;
        }
    }

    private final Clock clock;
    private final Executor workers;
    private final Logger logger;
    private final Map<UUID, StaffSessionSnapshot> active;
    private final Map<UUID, StaffRank> ranks;
    private final Set<UUID> transitions;

    StaffModeActivationCoordinator(
            Clock clock,
            Executor workers,
            Logger logger,
            Map<UUID, StaffSessionSnapshot> active,
            Map<UUID, StaffRank> ranks,
            Set<UUID> transitions
    ) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
        this.active = java.util.Objects.requireNonNull(active, "active");
        this.ranks = java.util.Objects.requireNonNull(ranks, "ranks");
        this.transitions = java.util.Objects.requireNonNull(transitions, "transitions");
    }

    boolean activate(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore store,
            StaffRank rank,
            ActivationPath path,
            Runnable applyStaffState,
            Runnable recovery,
            Consumer<String> playerMessage,
            String successMessage
    ) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(store, "store");
        java.util.Objects.requireNonNull(rank, "rank");
        java.util.Objects.requireNonNull(path, "path");
        java.util.Objects.requireNonNull(applyStaffState, "applyStaffState");
        java.util.Objects.requireNonNull(recovery, "recovery");
        java.util.Objects.requireNonNull(playerMessage, "playerMessage");
        java.util.Objects.requireNonNull(successMessage, "successMessage");

        try {
            applyStaffState.run();
            active.put(playerId, session);
            ranks.put(playerId, rank);
        } catch (RuntimeException exception) {
            active.remove(playerId);
            ranks.remove(playerId);
            logActivationFailure(playerId, session, path, exception);
            if (queueRecovery(playerId, session, store, path, recovery, playerMessage)) {
                safeMessage(
                        playerId,
                        session,
                        playerMessage,
                        "Staff mode activation failed; your durable snapshot is preserved and recovery is pending."
                );
            } else {
                safeMessage(
                        playerId,
                        session,
                        playerMessage,
                        "Staff mode activation failed; recovery could not be queued and interaction remains blocked."
                );
            }
            return false;
        }

        transitions.remove(playerId);
        safeMessage(playerId, session, playerMessage, successMessage);
        return true;
    }

    private boolean queueRecovery(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore store,
            ActivationPath path,
            Runnable recovery,
            Consumer<String> playerMessage
    ) {
        try {
            workers.execute(() -> {
                try {
                    store.recoveryRequired(
                            session.sessionId(),
                            "Staff state activation failed during " + path.description,
                            clock.instant()
                    );
                    recovery.run();
                } catch (RuntimeException exception) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(
                                Level.SEVERE,
                                "Staff activation recovery persistence failed for player " + playerId
                                        + " session " + session.sessionId() + " during " + path.description,
                                exception
                        );
                    }
                    safeMessage(
                            playerId,
                            session,
                            playerMessage,
                            "Your durable staff snapshot requires administrator recovery; interaction remains blocked."
                    );
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            logger.log(
                    Level.SEVERE,
                    "Staff activation recovery queue rejected for player " + playerId
                            + " session " + session.sessionId() + " during " + path.description,
                    exception
            );
            return false;
        }
    }

    private void logActivationFailure(
            UUID playerId,
            StaffSessionSnapshot session,
            ActivationPath path,
            RuntimeException exception
    ) {
        logger.log(
                Level.SEVERE,
                "Staff state activation failed for player " + playerId
                        + " session " + session.sessionId() + " during " + path.description,
                exception
        );
    }

    private void safeMessage(
            UUID playerId,
            StaffSessionSnapshot session,
            Consumer<String> playerMessage,
            String message
    ) {
        try {
            playerMessage.accept(message);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Staff activation message failed for player " + playerId
                            + " session " + session.sessionId(),
                    exception
            );
        }
    }
}
