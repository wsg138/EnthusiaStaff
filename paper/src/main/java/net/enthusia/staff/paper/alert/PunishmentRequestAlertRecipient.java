package net.enthusia.staff.paper.alert;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

public record PunishmentRequestAlertRecipient(
        UUID playerId,
        String playerName,
        StaffRank rank
) {
    public PunishmentRequestAlertRecipient {
        Objects.requireNonNull(playerId, "playerId");
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("player name must be present");
        }
    }
}
