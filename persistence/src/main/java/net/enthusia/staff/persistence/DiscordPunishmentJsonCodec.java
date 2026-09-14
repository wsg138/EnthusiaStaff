package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPermissionSnapshot;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;

final class DiscordPunishmentJsonCodec {
    private static final int DOCUMENT_VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    String encode(DiscordPunishment punishment) {
        try {
            return mapper.writeValueAsString(Document.from(punishment));
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode Discord punishment state", exception);
        }
    }

    String encodeObservation(DiscordPunishment punishment) {
        try {
            return mapper.writeValueAsString(Observation.from(punishment));
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode Discord punishment observation", exception);
        }
    }

    DiscordPunishment decode(String json) {
        try {
            Document document = mapper.readValue(json, Document.class);
            if (document.version() != DOCUMENT_VERSION) {
                throw new IllegalArgumentException("unsupported Discord punishment document version");
            }
            return document.toDomain();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to decode Discord punishment state", exception);
        }
    }

    private record Document(
            int version,
            UUID punishmentId,
            UUID subjectId,
            String targetUserId,
            String guildId,
            UUID issuerId,
            String issuerName,
            String issuerRank,
            String type,
            String lengthKind,
            Long durationSeconds,
            boolean customDuration,
            boolean customConsequence,
            String restrictionKind,
            String restrictionSnowflake,
            String restrictionMode,
            String publicReason,
            String internalExplanation,
            int messageDeleteSeconds,
            boolean notifyTarget,
            Instant issuedAt,
            Instant expiresAt,
            String state,
            String termination,
            String dmOutcome,
            String removalDmOutcome,
            boolean externalApplied,
            Boolean previousOverrideExisted,
            Long previousAllowedRaw,
            Long previousDeniedRaw,
            String lastErrorCode,
            String lastTransitionOperationKey
    ) {
        static Document from(DiscordPunishment punishment) {
            DiscordPunishmentIntent intent = punishment.intent();
            DiscordRestrictionTarget restriction = intent.restriction().orElse(null);
            DiscordPermissionSnapshot snapshot = punishment.previousRestriction().orElse(null);
            return new Document(
                    DOCUMENT_VERSION,
                    punishment.punishmentId(),
                    punishment.subjectId().value(),
                    punishment.targetUserId().value(),
                    punishment.guildId().value(),
                    punishment.issuer().id(),
                    punishment.issuer().displayName(),
                    punishment.issuer().rank().name(),
                    intent.type().name(),
                    intent.length().kind().name(),
                    intent.length().temporary().map(Duration::toSeconds).orElse(null),
                    intent.customDuration(),
                    intent.customConsequence(),
                    restriction == null ? null : restriction.kind().name(),
                    restriction == null ? null : restriction.snowflake(),
                    restriction == null ? null : restriction.mode().name(),
                    intent.publicReason(),
                    intent.internalExplanation(),
                    intent.messageDeleteSeconds(),
                    intent.notifyTarget(),
                    punishment.issuedAt(),
                    punishment.expiresAt().orElse(null),
                    punishment.state().name(),
                    punishment.termination().name(),
                    punishment.dmOutcome().name(),
                    punishment.removalDmOutcome().name(),
                    punishment.externalApplied(),
                    snapshot == null ? null : snapshot.existed(),
                    snapshot == null ? null : snapshot.allowedRaw(),
                    snapshot == null ? null : snapshot.deniedRaw(),
                    punishment.lastErrorCode().orElse(null),
                    punishment.lastTransitionOperationKey().orElse(null)
            );
        }

        DiscordPunishment toDomain() {
            DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                    DiscordConsequenceType.valueOf(type),
                    length(),
                    customDuration,
                    customConsequence,
                    restriction(),
                    publicReason,
                    internalExplanation,
                    messageDeleteSeconds,
                    notifyTarget
            );
            return new DiscordPunishment(
                    punishmentId,
                    new ModerationSubjectId(subjectId),
                    new DiscordUserId(targetUserId),
                    new DiscordGuildId(guildId),
                    new Actor(issuerId, issuerName, StaffRank.valueOf(issuerRank)),
                    intent,
                    issuedAt,
                    Optional.ofNullable(expiresAt),
                    DiscordPunishmentState.valueOf(state),
                    DiscordPunishmentTermination.valueOf(termination),
                    delivery(dmOutcome),
                    delivery(removalDmOutcome),
                    externalApplied,
                    permissionSnapshot(),
                    Optional.ofNullable(lastErrorCode),
                    Optional.ofNullable(lastTransitionOperationKey)
            );
        }

        private static DiscordDeliveryOutcome delivery(String value) {
            if (value == null) {
                return DiscordDeliveryOutcome.NOT_ATTEMPTED;
            }
            if ("FAILED".equals(value)) {
                return DiscordDeliveryOutcome.FAILED_TERMINAL;
            }
            return DiscordDeliveryOutcome.valueOf(value);
        }

        private SanctionLength length() {
            SanctionLength.Kind kind = SanctionLength.Kind.valueOf(lengthKind);
            return switch (kind) {
                case INSTANT -> SanctionLength.instant();
                case PERMANENT -> SanctionLength.permanent();
                case TEMPORARY -> SanctionLength.temporary(Duration.ofSeconds(requireDuration()));
            };
        }

        private long requireDuration() {
            if (durationSeconds == null || durationSeconds <= 0) {
                throw new IllegalArgumentException("temporary punishment duration is invalid");
            }
            return durationSeconds;
        }

        private Optional<DiscordRestrictionTarget> restriction() {
            if (restrictionKind == null && restrictionSnowflake == null && restrictionMode == null) {
                return Optional.empty();
            }
            if (restrictionKind == null || restrictionSnowflake == null || restrictionMode == null) {
                throw new IllegalArgumentException("stored Discord restriction is incomplete");
            }
            return Optional.of(new DiscordRestrictionTarget(
                    DiscordRestrictionTarget.Kind.valueOf(restrictionKind),
                    restrictionSnowflake,
                    DiscordRestrictionTarget.Mode.valueOf(restrictionMode)
            ));
        }

        private Optional<DiscordPermissionSnapshot> permissionSnapshot() {
            if (previousOverrideExisted == null && previousAllowedRaw == null && previousDeniedRaw == null) {
                return Optional.empty();
            }
            if (previousOverrideExisted == null || previousAllowedRaw == null || previousDeniedRaw == null) {
                throw new IllegalArgumentException("stored permission snapshot is incomplete");
            }
            return Optional.of(new DiscordPermissionSnapshot(
                    previousOverrideExisted,
                    previousAllowedRaw,
                    previousDeniedRaw
            ));
        }
    }

    private record Observation(
            boolean externalApplied,
            String dmOutcome,
            String removalDmOutcome,
            Boolean previousOverrideExisted,
            Long previousAllowedRaw,
            Long previousDeniedRaw,
            String lastErrorCode
    ) {
        static Observation from(DiscordPunishment punishment) {
            DiscordPermissionSnapshot snapshot = punishment.previousRestriction().orElse(null);
            return new Observation(
                    punishment.externalApplied(),
                    punishment.dmOutcome().name(),
                    punishment.removalDmOutcome().name(),
                    snapshot == null ? null : snapshot.existed(),
                    snapshot == null ? null : snapshot.allowedRaw(),
                    snapshot == null ? null : snapshot.deniedRaw(),
                    punishment.lastErrorCode().orElse(null)
            );
        }
    }
}
