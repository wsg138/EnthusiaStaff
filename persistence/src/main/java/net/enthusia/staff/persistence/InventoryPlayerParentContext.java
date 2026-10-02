package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Carries a pending inventory-player FK parent into the delegate's real JDBC transaction. */
final class InventoryPlayerParentContext {
    private static final String ENSURE_PLAYER = """
            INSERT INTO players(player_id, platform, first_seen_at, last_seen_at)
            VALUES (?, 'UNKNOWN', ?, ?)
            ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)
            """;
    private static final ThreadLocal<PendingPlayer> PENDING = new ThreadLocal<>();

    private InventoryPlayerParentContext() {
    }

    static <T> T withPlayer(UUID playerId, Instant observedAt, Supplier<T> work) {
        if (work == null) {
            throw new IllegalArgumentException("inventory transaction work must be present");
        }
        if (playerId == null || observedAt == null) {
            return work.get();
        }
        PendingPlayer previous = PENDING.get();
        PENDING.set(new PendingPlayer(playerId, observedAt));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                PENDING.remove();
            } else {
                PENDING.set(previous);
            }
        }
    }

    static void ensure(Connection connection) throws SQLException {
        PendingPlayer pending = PENDING.get();
        if (pending == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(ENSURE_PLAYER)) {
            statement.setBytes(1, UuidBytes.toBytes(pending.playerId()));
            statement.setTimestamp(2, Timestamp.from(pending.observedAt()));
            statement.setTimestamp(3, Timestamp.from(pending.observedAt()));
            statement.executeUpdate();
        }
    }

    private record PendingPlayer(UUID playerId, Instant observedAt) {
    }
}
