package net.enthusia.staff.paper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
    private final Clock clock;
    private final String serverId;
    private final Supplier<PlayerDirectory> players;
    private final Consumer<Runnable> submitter;
    private final Logger logger;

    PaperPresenceListener(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<PlayerDirectory> players,
            ExecutorService workers
    ) {
        this(clock, serverId, players, workers::execute, plugin.getLogger());
    }

    PaperPresenceListener(
            Clock clock,
            String serverId,
            Supplier<PlayerDirectory> players,
            Consumer<Runnable> submitter,
            Logger logger
    ) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must be present");
        }
        this.serverId = serverId;
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.submitter = java.util.Objects.requireNonNull(submitter, "submitter");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recordDisconnected(event.getPlayer().getUniqueId());
    }

    void recordDisconnected(UUID playerId) {
        Instant disconnectedAt = clock.instant();
        try {
            submitter.accept(() -> persistDisconnect(playerId, disconnectedAt));
        } catch (RejectedExecutionException exception) {
            logger.warning("Paper presence disconnect skipped because the bounded queue is full");
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
