package net.enthusia.staff.domain.discord;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Authoritative durable Discord punishment aggregate. */
public record DiscordPunishment(
        UUID punishmentId,
        ModerationSubjectId subjectId,
        DiscordUserId targetUserId,
        DiscordGuildId guildId,
        Actor issuer,
        DiscordPunishmentIntent intent,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        DiscordPunishmentState state,
        DiscordPunishmentTermination termination,
        DiscordDeliveryOutcome dmOutcome,
        DiscordDeliveryOutcome removalDmOutcome,
        boolean externalApplied,
        Optional<DiscordPermissionSnapshot> previousRestriction,
        Optional<String> lastErrorCode,
        Optional<String> lastTransitionOperationKey
) {
    private static final int MAX_ERROR_CODE = 96;
    private static final int MAX_OPERATION_KEY = 128;

    public DiscordPunishment {
        requireCore(punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt);
        requireState(expiresAt, state, termination, dmOutcome, removalDmOutcome, previousRestriction,
                lastErrorCode, lastTransitionOperationKey);
        validateExpiry(intent, issuedAt, expiresAt);
        validateRestrictionObservation(intent, previousRestriction);
        lastErrorCode.ifPresent(value -> bounded(value, "lastErrorCode", MAX_ERROR_CODE));
        lastTransitionOperationKey.ifPresent(value -> bounded(value, "lastTransitionOperationKey", MAX_OPERATION_KEY));
    }

    public static DiscordPunishment pending(
            UUID punishmentId,
            ModerationSubjectId subjectId,
            DiscordUserId targetUserId,
            DiscordGuildId guildId,
            Actor issuer,
            DiscordPunishmentIntent intent,
            Instant issuedAt,
            String operationKey
    ) {
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt,
                intent.length().expirationFrom(issuedAt),
                DiscordPunishmentState.PENDING_APPLY,
                DiscordPunishmentTermination.NONE,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                DiscordDeliveryOutcome.NOT_ATTEMPTED,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.of(operationKey)
        );
    }

    public DiscordPunishment withProcessingResult(
            DiscordPunishmentState nextState,
            DiscordDeliveryOutcome delivery,
            boolean applied,
            Optional<DiscordPermissionSnapshot> previous,
            Optional<String> errorCode,
            String operationKey
    ) {
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                nextState, termination, delivery, removalDmOutcome, applied, previous, errorCode, Optional.of(operationKey)
        );
    }

    public DiscordPunishment withApplyDeliveryOutcome(
            DiscordDeliveryOutcome delivery,
            Optional<String> errorCode,
            String operationKey
    ) {
        return withDeliveryOutcomes(delivery, removalDmOutcome, errorCode, operationKey);
    }

    public DiscordPunishment withRemovalDeliveryOutcome(
            DiscordDeliveryOutcome delivery,
            Optional<String> errorCode,
            String operationKey
    ) {
        return withDeliveryOutcomes(dmOutcome, delivery, errorCode, operationKey);
    }

    public DiscordPunishment requestRemoval(DiscordPunishmentTermination requested, String operationKey) {
        if (requested == null || requested == DiscordPunishmentTermination.NONE) {
            throw new IllegalArgumentException("removal termination must be explicit");
        }
        if (!intent.reversible()) {
            return markTerminal(requested, operationKey);
        }
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                DiscordPunishmentState.PENDING_REMOVE, requested, dmOutcome, DiscordDeliveryOutcome.NOT_ATTEMPTED,
                externalApplied, previousRestriction, Optional.empty(), Optional.of(operationKey)
        );
    }

    public DiscordPunishment markRemoved(String operationKey) {
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                terminalState(termination), termination, dmOutcome, removalDmOutcome, false,
                previousRestriction, Optional.empty(), Optional.of(operationKey)
        );
    }

    public boolean nativeBan() {
        return intent.type() == DiscordConsequenceType.BAN;
    }

    private DiscordPunishment withDeliveryOutcomes(
            DiscordDeliveryOutcome applyDelivery,
            DiscordDeliveryOutcome removalDelivery,
            Optional<String> errorCode,
            String operationKey
    ) {
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                state, termination, applyDelivery, removalDelivery, externalApplied,
                previousRestriction, errorCode, Optional.of(operationKey)
        );
    }

    private DiscordPunishment markTerminal(DiscordPunishmentTermination requested, String operationKey) {
        return new DiscordPunishment(
                punishmentId, subjectId, targetUserId, guildId, issuer, intent, issuedAt, expiresAt,
                terminalState(requested), requested, dmOutcome, removalDmOutcome, externalApplied,
                previousRestriction, Optional.empty(), Optional.of(operationKey)
        );
    }

    private static DiscordPunishmentState terminalState(DiscordPunishmentTermination termination) {
        return switch (termination) {
            case END -> DiscordPunishmentState.ENDED;
            case REVOKE -> DiscordPunishmentState.REVOKED;
            case OVERTURN -> DiscordPunishmentState.OVERTURNED;
            case EXPIRE -> DiscordPunishmentState.EXPIRED;
            case NONE -> throw new IllegalStateException("terminal punishment requires termination");
        };
    }

    private static void requireCore(Object... values) {
        for (Object value : values) {
            Objects.requireNonNull(value, "punishment fields must be present");
        }
    }

    private static void requireState(
            Optional<Instant> expiresAt,
            DiscordPunishmentState state,
            DiscordPunishmentTermination termination,
            DiscordDeliveryOutcome dmOutcome,
            DiscordDeliveryOutcome removalDmOutcome,
            Optional<DiscordPermissionSnapshot> previousRestriction,
            Optional<String> lastErrorCode,
            Optional<String> lastTransitionOperationKey
    ) {
        if (expiresAt == null || state == null || termination == null || dmOutcome == null
                || removalDmOutcome == null || previousRestriction == null || lastErrorCode == null
                || lastTransitionOperationKey == null) {
            throw new IllegalArgumentException("punishment state fields must be present");
        }
        if (state.removalPending() && termination == DiscordPunishmentTermination.NONE) {
            throw new IllegalArgumentException("removal state requires a terminal intent");
        }
    }

    private static void validateExpiry(
            DiscordPunishmentIntent intent,
            Instant issuedAt,
            Optional<Instant> expiresAt
    ) {
        Optional<Instant> expected = intent.length().expirationFrom(issuedAt);
        if (!expected.equals(expiresAt)) {
            throw new IllegalArgumentException("expiration must match sanction length");
        }
    }

    private static void validateRestrictionObservation(
            DiscordPunishmentIntent intent,
            Optional<DiscordPermissionSnapshot> snapshot
    ) {
        if (snapshot.isPresent() && intent.type() != DiscordConsequenceType.CHANNEL_RESTRICTION) {
            throw new IllegalArgumentException("permission snapshot is only valid for a channel restriction");
        }
    }

    private static void bounded(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
