package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Durable suspicion alert only; it never represents or authorizes an automatic punishment. */
public record EvasionAlert(
        UUID alertId,
        String operationKey,
        Context context,
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
    private static final int MAX_SUMMARY = 512;

    public enum State {
        OPEN,
        RESOLVED
    }

    public enum DeliveryState {
        PENDING,
        DELIVERED,
        RETRY
    }

    public enum TriggerType {
        LINKED_MINECRAFT_ONLINE
    }

    public record Context(
            ModerationSubjectId subjectId,
            UUID punishmentId,
            DiscordUserId targetDiscordUserId,
            DiscordConsequenceType punishmentType,
            String punishmentSummary,
            DiscordPunishmentState punishmentState,
            Optional<Instant> punishmentExpiresAt,
            UUID triggeringMinecraftPlayerId,
            Optional<String> triggeringMinecraftUsername,
            String currentServer,
            long playerRevision,
            TriggerType triggerType,
            Instant triggeredAt
    ) {
        public Context {
            if (subjectId == null || punishmentId == null || targetDiscordUserId == null || punishmentType == null
                    || blank(punishmentSummary) || punishmentSummary.length() > MAX_SUMMARY || punishmentState == null
                    || punishmentState.terminal() || punishmentExpiresAt == null || triggeringMinecraftPlayerId == null
                    || triggeringMinecraftUsername == null || blank(currentServer) || currentServer.length() > 64
                    || playerRevision < 0 || triggerType == null || triggeredAt == null) {
                throw new IllegalArgumentException("evasion alert context must describe an active punishment and trigger");
            }
            if (punishmentExpiresAt.isPresent()
                    && !punishmentExpiresAt.orElseThrow().isAfter(triggeredAt)) {
                throw new IllegalArgumentException(
                        "evasion alert punishment must be active at the trigger time");
            }
            triggeringMinecraftUsername.ifPresent(username -> {
                if (blank(username) || username.length() > 32) {
                    throw new IllegalArgumentException("triggering Minecraft username is invalid");
                }
            });
        }
    }

    public EvasionAlert {
        if (alertId == null || blank(operationKey) || operationKey.length() > 160 || context == null || state == null
                || discordDelivery == null || minecraftDelivery == null || discordAttempts < 0
                || minecraftAttempts < 0 || discordErrorCode == null || minecraftErrorCode == null
                || discordNextAttemptAt == null || minecraftNextAttemptAt == null || createdAt == null
                || updatedAt == null || updatedAt.isBefore(createdAt) || revision < 0) {
            throw new IllegalArgumentException("evasion alert fields must be present and valid");
        }
        validateDelivery(discordDelivery, discordErrorCode, discordNextAttemptAt);
        validateDelivery(minecraftDelivery, minecraftErrorCode, minecraftNextAttemptAt);
    }

    public ModerationSubjectId subjectId() {
        return context.subjectId();
    }

    public UUID punishmentId() {
        return context.punishmentId();
    }

    public DiscordUserId targetDiscordUserId() {
        return context.targetDiscordUserId();
    }

    public DiscordConsequenceType punishmentType() {
        return context.punishmentType();
    }

    public String punishmentSummary() {
        return context.punishmentSummary();
    }

    public DiscordPunishmentState punishmentState() {
        return context.punishmentState();
    }

    public Optional<Instant> punishmentExpiresAt() {
        return context.punishmentExpiresAt();
    }

    public UUID triggeringMinecraftPlayerId() {
        return context.triggeringMinecraftPlayerId();
    }

    public Optional<String> triggeringMinecraftUsername() {
        return context.triggeringMinecraftUsername();
    }

    public String currentServer() {
        return context.currentServer();
    }

    public long playerRevision() {
        return context.playerRevision();
    }

    public TriggerType triggerType() {
        return context.triggerType();
    }

    public Instant triggeredAt() {
        return context.triggeredAt();
    }

    private static void validateDelivery(
            DeliveryState delivery,
            Optional<String> errorCode,
            Optional<Instant> nextAttemptAt
    ) {
        switch (delivery) {
            case DELIVERED -> requireDeliveredState(errorCode, nextAttemptAt);
            case PENDING -> requirePendingState(errorCode, nextAttemptAt);
            case RETRY -> requireRetryState(errorCode, nextAttemptAt);
            default -> throw new IllegalStateException("unsupported alert delivery state");
        }
    }

    private static void requireDeliveredState(Optional<String> errorCode, Optional<Instant> nextAttemptAt) {
        if (errorCode.isPresent() || nextAttemptAt.isPresent()) {
            throw new IllegalArgumentException("delivered alert channel cannot retain retry state");
        }
    }

    private static void requirePendingState(Optional<String> errorCode, Optional<Instant> nextAttemptAt) {
        if (errorCode.isPresent() || nextAttemptAt.isEmpty()) {
            throw new IllegalArgumentException("pending alert channel requires a due time and no error");
        }
    }

    private static void requireRetryState(Optional<String> errorCode, Optional<Instant> nextAttemptAt) {
        if (errorCode.isEmpty() || nextAttemptAt.isEmpty()) {
            throw new IllegalArgumentException("retry alert channel requires error and due time");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
