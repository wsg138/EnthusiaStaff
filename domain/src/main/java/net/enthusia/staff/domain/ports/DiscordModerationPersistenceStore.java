package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.EnforcementTarget;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/**
 * Durable Discord moderation state owned by the authoritative EnthusiaStaff database.
 * Implementations must make mutations transactional and idempotent where an operation key is supplied.
 */
public interface DiscordModerationPersistenceStore {
    VersionedSubject ensureMinecraftSubject(UUID playerId, Instant now);

    VersionedSubject ensureDiscordSubject(DiscordUserId userId, Instant now);

    Optional<VersionedSubject> subjectForMinecraft(UUID playerId);

    Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId);

    Optional<VersionedLink> currentLink(UUID playerId);

    VersionedLink link(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant linkedAt
    );

    VersionedLink unlink(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            long expectedRevision,
            String operationKey,
            Instant unlinkedAt
    );

    /** Atomically closes any current link and establishes the new STAFF_RECOVERY owner. */
    VersionedLink reassign(
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            String operationKey,
            Instant changedAt
    );

    VersionedSubject setMainMinecraftAccount(
            ModerationSubjectId subjectId,
            MainMinecraftAccount mainAccount,
            long expectedSubjectRevision,
            Instant selectedAt
    );

    StoredEnforcementTarget recordEnforcementTarget(
            ModerationSubjectId subjectId,
            EnforcementTarget target,
            String operationKey,
            Instant now
    );

    StoredEvidence recordEvidence(EvidenceMetadata metadata);

    SecurityLock activateSecurityLock(
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            String reasonCode,
            String operationKey,
            Instant now
    );

    SecurityLock releaseSecurityLock(
            UUID lockId,
            long expectedRevision,
            String operationKey,
            Instant now
    );

    ReconciliationState saveReconciliation(
            ReconciliationState state,
            long expectedRevision,
            Instant now
    );

    MaintenanceWork enqueueMaintenance(
            String workType,
            String resourceKey,
            Instant dueAt,
            Instant now
    );

    List<MaintenanceWork> claimDueMaintenance(
            Instant now,
            int limit,
            String leaseOwner,
            Instant leaseUntil
    );

    boolean completeMaintenance(
            UUID workId,
            long expectedRevision,
            String leaseOwner,
            Instant now
    );

    record VersionedSubject(ModerationSubject subject, long revision) {
        public VersionedSubject {
            if (subject == null || revision < 0) {
                throw new IllegalArgumentException("versioned subject fields are invalid");
            }
        }
    }

    record VersionedLink(
            UUID linkId,
            ModerationSubjectId subjectId,
            DiscordMinecraftLink link,
            long revision,
            boolean replayed
    ) {
        public VersionedLink {
            if (linkId == null || subjectId == null || link == null || revision < 0) {
                throw new IllegalArgumentException("versioned link fields are invalid");
            }
        }
    }

    record StoredEnforcementTarget(
            UUID targetId,
            ModerationSubjectId subjectId,
            EnforcementTarget target,
            String state,
            long revision,
            boolean replayed,
            Instant createdAt,
            Instant updatedAt
    ) {
        public StoredEnforcementTarget {
            if (targetId == null || subjectId == null || target == null || state == null
                    || state.isBlank() || revision < 0 || createdAt == null || updatedAt == null) {
                throw new IllegalArgumentException("stored enforcement target fields are invalid");
            }
        }
    }

    record EvidenceMetadata(
            UUID evidenceId,
            String operationKey,
            ModerationSubjectId subjectId,
            Optional<String> caseId,
            String guildId,
            String channelId,
            String messageId,
            DiscordUserId authorUserId,
            Instant capturedAt,
            Instant retainUntil,
            String metadataJson
    ) {
        public EvidenceMetadata {
            if (evidenceId == null || blank(operationKey) || subjectId == null || caseId == null
                    || blank(guildId) || blank(channelId) || blank(messageId) || authorUserId == null
                    || capturedAt == null || retainUntil == null || blank(metadataJson)
                    || retainUntil.isBefore(capturedAt)) {
                throw new IllegalArgumentException("Discord evidence metadata fields are invalid");
            }
            caseId.ifPresent(value -> {
                if (value.isBlank() || value.length() > 16) {
                    throw new IllegalArgumentException("caseId must be nonblank and at most 16 characters");
                }
            });
        }
    }

    record StoredEvidence(
            UUID evidenceId,
            long revision,
            boolean replayed,
            Instant retainUntil,
            String purgeState
    ) {
        public StoredEvidence {
            if (evidenceId == null || revision < 0 || retainUntil == null || blank(purgeState)) {
                throw new IllegalArgumentException("stored evidence fields are invalid");
            }
        }
    }

    record SecurityLock(
            UUID lockId,
            ModerationSubjectId subjectId,
            DiscordUserId discordUserId,
            String reasonCode,
            String state,
            Instant lockedAt,
            Optional<Instant> releasedAt,
            long revision,
            boolean replayed
    ) {
        public SecurityLock {
            if (lockId == null || subjectId == null || discordUserId == null || blank(reasonCode)
                    || blank(state) || lockedAt == null || releasedAt == null || revision < 0) {
                throw new IllegalArgumentException("security lock fields are invalid");
            }
        }
    }

    record ReconciliationState(
            String reconciliationKey,
            String resourceType,
            String resourceId,
            String desiredStateJson,
            Optional<String> observedStateJson,
            String state,
            int attemptCount,
            Optional<Instant> nextAttemptAt,
            Optional<String> lastErrorCode,
            long revision
    ) {
        public ReconciliationState {
            if (blank(reconciliationKey) || blank(resourceType) || blank(resourceId)
                    || blank(desiredStateJson) || observedStateJson == null || blank(state)
                    || attemptCount < 0 || nextAttemptAt == null || lastErrorCode == null || revision < 0) {
                throw new IllegalArgumentException("reconciliation fields are invalid");
            }
        }
    }

    record MaintenanceWork(
            UUID workId,
            String workType,
            String resourceKey,
            Instant dueAt,
            String state,
            Optional<String> leaseOwner,
            Optional<Instant> leaseUntil,
            int attemptCount,
            long revision
    ) {
        public MaintenanceWork {
            if (workId == null || blank(workType) || blank(resourceKey) || dueAt == null || blank(state)
                    || leaseOwner == null || leaseUntil == null || attemptCount < 0 || revision < 0) {
                throw new IllegalArgumentException("maintenance work fields are invalid");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
