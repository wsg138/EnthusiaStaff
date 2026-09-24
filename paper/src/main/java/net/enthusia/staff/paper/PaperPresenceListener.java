package net.enthusia.staff.paper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps authoritative presence correct when Paper is operating without a Velocity disconnect observation.
 */
final class PaperPresenceListener implements Listener {
    private static final int MAX_SUBMISSION_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;

    private final Clock clock;
    private final String serverId;
    private final Supplier<PlayerDirectory> players;
    private final Consumer<Runnable> submitter;
    private final Consumer<Runnable> retryScheduler;
    private final Logger logger;

    PaperPresenceListener(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<PlayerDirectory> players,
            ExecutorService workers
    ) {
        this(
                clock, serverId, players, workers::execute,
                retry -> plugin.getServer().getAsyncScheduler().runDelayed(
                        plugin, ignored -> retry.run(), RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS
                ),
                plugin.getLogger()
        );
    }

    PaperPresenceListener(
            Clock clock,
            String serverId,
            Supplier<PlayerDirectory> players,
            Consumer<Runnable> submitter,
            Consumer<Runnable> retryScheduler,
            Logger logger
    ) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must be present");
        }
        this.serverId = serverId;
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.submitter = java.util.Objects.requireNonNull(submitter, "submitter");
        this.retryScheduler = java.util.Objects.requireNonNull(retryScheduler, "retryScheduler");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recordDisconnected(event.getPlayer().getUniqueId());
    }

    void recordDisconnected(UUID playerId) {
        submitDisconnect(playerId, clock.instant(), 1);
    }

    private void submitDisconnect(UUID playerId, Instant disconnectedAt, int attempt) {
        try {
            submitter.accept(() -> persistDisconnect(playerId, disconnectedAt));
        } catch (RejectedExecutionException exception) {
            scheduleRetry(playerId, disconnectedAt, attempt);
        }
    }

    private void scheduleRetry(UUID playerId, Instant disconnectedAt, int attempt) {
        if (attempt >= MAX_SUBMISSION_ATTEMPTS) {
            logger.severe("Paper presence disconnect could not be queued after bounded retries");
            return;
        }
        try {
            retryScheduler.accept(() -> submitDisconnect(playerId, disconnectedAt, attempt + 1));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Paper presence disconnect retry scheduling failed", exception);
        }
    }

    private void persistDisconnect(UUID playerId, Instant disconnectedAt) {
        PlayerDirectory directory = players.get();
        if (directory == null) {
            logger.warning("Paper presence disconnect skipped because player storage is unavailable");
            return;
        }
        try {
            directory.recordDisconnected(playerId, serverId, disconnectedAt);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Paper presence disconnect persistence failed", exception);
        }
    }
}
