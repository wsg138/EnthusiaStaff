package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.moderation.DiscordGuildId;

/** Durable D07 punishment intent, work leasing, and reconciliation state. */
public interface DiscordPunishmentRepository {
    StoredPunishment create(DiscordPunishment punishment, String operationKey, Instant now);

    Optional<StoredPunishment> find(UUID punishmentId);

    StoredPunishment transition(
            StoredPunishment expected,
            DiscordPunishment replacement,
            String operationKey,
            List<WorkSchedule> work,
            Instant now
    );

    List<WorkLease> claimDue(Instant now, int limit, String leaseOwner, Instant leaseUntil);

    StoredPunishment settle(
            WorkLease work,
            StoredPunishment expected,
            DiscordPunishment replacement,
            List<WorkSchedule> nextWork,
            Instant now
    );

    List<StoredPunishment> activeNativeBans(DiscordGuildId guildId, int limit);

    enum WorkType {
        APPLY,
        REMOVE,
        RECONCILE
    }

    record StoredPunishment(DiscordPunishment punishment, long revision, boolean replayed) {
        public StoredPunishment {
            if (punishment == null || revision < 0) {
                throw new IllegalArgumentException("stored punishment fields are invalid");
            }
        }
    }

    record WorkSchedule(WorkType type, Instant dueAt) {
        public WorkSchedule {
            if (type == null || dueAt == null) {
                throw new IllegalArgumentException("work schedule fields must be present");
            }
        }
    }

    record WorkLease(
            UUID workId,
            WorkType type,
            UUID punishmentId,
            Instant dueAt,
            String leaseOwner,
            Instant leaseUntil,
            int attemptCount,
            long revision
    ) {
        public WorkLease {
            if (workId == null || type == null || punishmentId == null || dueAt == null
                    || leaseOwner == null || leaseOwner.isBlank() || leaseUntil == null
                    || attemptCount < 1 || revision < 1) {
                throw new IllegalArgumentException("work lease fields are invalid");
            }
        }
    }
}
