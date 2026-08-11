package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionStatus;

sealed interface ExactSanctionMutationDecision {
    record Apply(
            SanctionStatus resultingStatus,
            Optional<Instant> resultingExpiration,
            Optional<Instant> resultingEndedAt
    ) implements ExactSanctionMutationDecision {
        public Apply {
            Objects.requireNonNull(resultingStatus, "resultingStatus");
            Objects.requireNonNull(resultingExpiration, "resultingExpiration");
            Objects.requireNonNull(resultingEndedAt, "resultingEndedAt");
        }
    }

    record NoChange(ExactSanctionChangeResult.NoChange result)
            implements ExactSanctionMutationDecision {
        public NoChange {
            Objects.requireNonNull(result, "result");
        }
    }

    record Rejected(ExactSanctionChangeResult.Rejected result)
            implements ExactSanctionMutationDecision {
        public Rejected {
            Objects.requireNonNull(result, "result");
        }
    }
}
