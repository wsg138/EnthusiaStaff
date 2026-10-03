package net.enthusia.staff.domain.staff;

import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

/**
 * Lightweight cross-server staff state snapshot carried from the source backend to the
 * destination backend through the proxy during a server transfer.
 *
 * <p>This is intentionally small: it captures only the live staff-visibility state that the
 * destination needs <em>before</em> its join-message logic runs. The heavy durable state
 * (inventory snapshot, session row) keeps flowing through the existing database path; this
 * record is the authoritative in-memory truth for the transfer itself, and the database
 * remains the fallback when the record is missing.</p>
 */
public record StaffTransferSnapshot(
        UUID playerId,
        UUID transferId,
        String sourceServer,
        boolean vanished,
        boolean staffModeActive,
        StaffRank rank,
        String selectedGameMode,
        long capturedAtMillis
) {
    public StaffTransferSnapshot {
        if (playerId == null || transferId == null) {
            throw new IllegalArgumentException("player and transfer identifiers are required");
        }
        if (sourceServer == null || sourceServer.isBlank()) {
            throw new IllegalArgumentException("source server identifier is required");
        }
        if (selectedGameMode != null && selectedGameMode.isBlank()) {
            throw new IllegalArgumentException("selected game mode must be null or non-blank");
        }
        if (capturedAtMillis < 0) {
            throw new IllegalArgumentException("capture timestamp must be non-negative");
        }
    }
}
