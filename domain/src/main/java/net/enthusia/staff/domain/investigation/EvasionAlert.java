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
        Optional<Instant> discordNextAttemptAt,
        Optional<Instant> minecraftNextAttemptAt,
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
                || discordNextAttemptAt == null || minecraftNextAttemptAt == null || createdAt == null
                || updatedAt == null || updatedAt.isBefore(createdAt) || revision < 0) {
            throw new IllegalArgumentException("evasion alert fields must be present and valid");
        }
        validateDelivery(discordDelivery, discordErrorCode, discordNextAttemptAt);
        validateDelivery(minecraftDelivery, minecraftErrorCode, minecraftNextAttemptAt);
    }

    private static void validateDelivery(
            DeliveryState delivery,
            Optional<String> errorCode,
            Optional<Instant> nextAttemptAt
    ) {
        if (delivery == DeliveryState.DELIVERED && (errorCode.isPresent() || nextAttemptAt.isPresent())) {
            throw new IllegalArgumentException("delivered alert channel cannot retain retry state");
        }
        if (delivery == DeliveryState.PENDING && (errorCode.isPresent() || nextAttemptAt.isEmpty())) {
            throw new IllegalArgumentException("pending alert channel requires a due time and no error");
        }
        if (delivery == DeliveryState.RETRY && (errorCode.isEmpty() || nextAttemptAt.isEmpty())) {
            throw new IllegalArgumentException("retry alert channel requires error and due time");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
