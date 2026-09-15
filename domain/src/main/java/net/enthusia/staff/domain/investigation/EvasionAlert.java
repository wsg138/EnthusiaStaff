package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Durable suspicion alert only; it never represents or authorizes an automatic punishment. */
public record EvasionAlert(
        UUID alertId,
        String operationKey,
        ModerationSubjectId subjectId,
        UUID punishmentId,
        UUID triggeringMinecraftPlayerId,
        String currentServer,
        long playerRevision,
        State state,
        DeliveryState discordDelivery,
        DeliveryState minecraftDelivery,
        int discordAttempts,
        int minecraftAttempts,
        Optional<String> discordErrorCode,
        Optional<String> minecraftErrorCode,
        Instant createdAt,
        Instant updatedAt,
        long revision,
        boolean replayed
) {
    public enum State {
        OPEN,
        RESOLVED
    }

    public enum DeliveryState {
        PENDING,
        DELIVERED,
        RETRY
    }

    public EvasionAlert {
        if (alertId == null || blank(operationKey) || operationKey.length() > 160 || subjectId == null
                || punishmentId == null || triggeringMinecraftPlayerId == null || blank(currentServer)
                || currentServer.length() > 64 || playerRevision < 0 || state == null
                || discordDelivery == null || minecraftDelivery == null || discordAttempts < 0
                || minecraftAttempts < 0 || discordErrorCode == null || minecraftErrorCode == null
                || createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt) || revision < 0) {
            throw new IllegalArgumentException("evasion alert fields must be present and valid");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
