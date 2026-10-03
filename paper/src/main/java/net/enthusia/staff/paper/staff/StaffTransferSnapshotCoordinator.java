package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.enthusia.staff.protocol.TransferSnapshotMessages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates cross-server vanish/staff-mode state transfer on a Paper backend.
 *
 * <p>Source side: captures the live vanish/staff-mode state into a small in-memory snapshot
 * (fast, non-blocking reads only) and uploads it to the proxy over the persistent channel.
 * The upload is fire-and-forget: the transfer must never wait for it, let alone for a
 * database write.</p>
 *
 * <p>Destination side: stashes snapshots that arrive inside {@code STAFF_MODE_HANDOFF_PREPARE}
 * payloads so the join listener can apply them before the join-message logic runs. The
 * transferred snapshot is authoritative for the session; the database remains the fallback
 * when no snapshot arrived.</p>
 */
public final class StaffTransferSnapshotCoordinator {
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PENDING_TTL = Duration.ofSeconds(30);

    /**
     * Sends a channel message to the proxy. Bound once the persistent channel client is
     * available; may be {@code null} while the channel is down.
     */
    @FunctionalInterface
    public interface ChannelSender {
        CompletableFuture<Boolean> send(UUID messageId, String messageType, String payloadJson, Duration timeout);
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Clock clock;
    private final String serverId;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final Map<UUID, PendingSnapshot> pending = new ConcurrentHashMap<>();
    private volatile ChannelSender sender;

    public StaffTransferSnapshotCoordinator(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            StaffModeManager staffMode,
            VanishManager vanish
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.clock = Objects.requireNonNull(clock, "clock");
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId is required");
        }
        this.serverId = serverId;
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    /** Binds the proxy channel sender. Called once the persistent channel client is running. */
    public void bindSender(ChannelSender sender) {
        this.sender = sender;
    }

    /**
     * Captures the in-memory transfer snapshot and uploads it to the proxy.
     * Fast and non-blocking: reads concurrent maps only, never touches the database,
     * and never waits for the upload acknowledgement.
     */
    public void captureAndUpload(UUID playerId, UUID transferId) {
        if (playerId == null || transferId == null) {
            return;
        }
        ChannelSender current = sender;
        if (current == null) {
            logSkippedUpload(playerId);
            return;
        }
        StaffTransferSnapshot snapshot = captureOrNull(playerId, transferId);
        if (snapshot == null) {
            return;
        }
        String payload = encodeOrNull(snapshot, playerId);
        if (payload == null) {
            return;
        }
        uploadSnapshot(current, playerId, payload);
    }

    private void logSkippedUpload(UUID playerId) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Transfer snapshot upload skipped for " + playerId + ": channel sender is not bound");
        }
    }

    private StaffTransferSnapshot captureOrNull(UUID playerId, UUID transferId) {
        try {
            return capture(playerId, transferId);
        } catch (RuntimeException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                        "Cross-server transfer snapshot capture failed for " + playerId
                                + "; the transfer continues and the destination falls back to the database",
                        exception);
            }
            return null;
        }
    }

    private String encodeOrNull(StaffTransferSnapshot snapshot, UUID playerId) {
        try {
            return TransferSnapshotMessages.encode(snapshot);
        } catch (RuntimeException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                        "Cross-server transfer snapshot encoding failed for " + playerId, exception);
            }
            return null;
        }
    }

    private void uploadSnapshot(ChannelSender current, UUID playerId, String payload) {
        try {
            current.send(UUID.randomUUID(), TransferSnapshotMessages.UPLOAD, payload, UPLOAD_TIMEOUT)
                    .whenComplete((acknowledged, failure) -> {
                        if (failure != null || !Boolean.TRUE.equals(acknowledged)) {
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.log(Level.WARNING,
                                        "Cross-server transfer snapshot upload was not acknowledged for " + playerId
                                                + "; the destination will fall back to the database");
                            }
                        }
                    });
        } catch (RuntimeException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Cross-server transfer snapshot upload failed for " + playerId, exception);
            }
        }
    }

    private StaffTransferSnapshot capture(UUID playerId, UUID transferId) {
        boolean staffActive = staffMode.active(playerId);
        boolean vanished = vanish.isVanished(playerId);
        StaffRank rank = null;
        String selectedGameMode = vanish.transferSelectedGameMode(playerId)
                .map(GameMode::name)
                .orElse(null);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            rank = resolveRankSafely(player);
            if (selectedGameMode == null) {
                selectedGameMode = player.getGameMode().name();
            }
        }
        return new StaffTransferSnapshot(
                playerId,
                transferId,
                serverId,
                vanished,
                staffActive,
                rank,
                selectedGameMode,
                clock.millis()
        );
    }

    private StaffRank resolveRankSafely(Player player) {
        try {
            return PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Staff rank resolution raced with transfer capture", exception);
            return null;
        }
    }

    /** Stashes a snapshot received for this backend as a transfer destination. */
    public void stash(StaffTransferSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        pending.put(snapshot.playerId(),
                new PendingSnapshot(snapshot, clock.millis() + PENDING_TTL.toMillis()));
    }

    /**
     * Consumes the pending snapshot for a joining player. Empty when no snapshot arrived,
     * it expired, or the transfer id does not match: the caller then uses the database fallback.
     */
    public Optional<StaffTransferSnapshot> consume(UUID playerId) {
        return consume(playerId, null);
    }

    Optional<StaffTransferSnapshot> consume(UUID playerId, UUID transferId) {
        if (playerId == null) {
            return Optional.empty();
        }
        PendingSnapshot pendingSnapshot = pending.remove(playerId);
        if (pendingSnapshot == null || pendingSnapshot.expiresAtMillis() <= clock.millis()) {
            return Optional.empty();
        }
        StaffTransferSnapshot snapshot = pendingSnapshot.snapshot();
        if (transferId != null && !snapshot.transferId().equals(transferId)) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    private record PendingSnapshot(StaffTransferSnapshot snapshot, long expiresAtMillis) {
    }
}
