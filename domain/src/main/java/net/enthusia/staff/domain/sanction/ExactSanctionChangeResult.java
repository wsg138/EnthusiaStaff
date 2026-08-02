package net.enthusia.staff.domain.sanction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

public sealed interface ExactSanctionChangeResult {
    record Applied(
            CaseId caseId,
            UUID sanctionId,
            UUID subjectId,
            SanctionChangeAction action,
            SanctionStatus previousStatus,
            SanctionStatus resultingStatus,
            Optional<Instant> previousExpiration,
            Optional<Instant> resultingExpiration,
            Instant occurredAt,
            Optional<UUID> linkedAppealId,
            Optional<UUID> linkedPunishmentRequestId,
            boolean replayed
    ) implements ExactSanctionChangeResult {
        public Applied {
            if (caseId == null || sanctionId == null || subjectId == null || action == null
                    || previousStatus == null || resultingStatus == null
                    || previousExpiration == null || resultingExpiration == null
                    || occurredAt == null || linkedAppealId == null
                    || linkedPunishmentRequestId == null) {
                throw new IllegalArgumentException("applied sanction change fields must be present");
            }
        }
    }

    record NoChange(
            String code,
            String message,
            CaseId caseId,
            UUID sanctionId,
            SanctionStatus currentStatus,
            Optional<Instant> currentExpiration
    ) implements ExactSanctionChangeResult {
        public NoChange {
            if (code == null || code.isBlank() || message == null || message.isBlank()
                    || caseId == null || sanctionId == null || currentStatus == null
                    || currentExpiration == null) {
                throw new IllegalArgumentException("no-change fields must be present");
            }
        }
    }

    record Rejected(String code, String message) implements ExactSanctionChangeResult {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("rejection fields must be present");
            }
        }
    }
}
