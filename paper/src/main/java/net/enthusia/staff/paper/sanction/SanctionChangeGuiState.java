package net.enthusia.staff.paper.sanction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;

sealed interface SanctionChangeGuiState {
    UUID viewerId();

    String commandName();

    String targetLabel();

    record Cases(
            UUID viewerId,
            String commandName,
            String targetLabel,
            List<CaseReview> cases,
            int page
    ) implements SanctionChangeGuiState {
        public Cases {
            validate(viewerId, commandName, targetLabel);
            if (cases == null || cases.isEmpty() || page < 0) {
                throw new IllegalArgumentException("case selection state must contain cases and a valid page");
            }
            cases = List.copyOf(cases);
        }
    }

    record Actions(
            UUID viewerId,
            String commandName,
            String targetLabel,
            CaseReview review,
            Optional<Cases> origin
    ) implements SanctionChangeGuiState {
        public Actions {
            validate(viewerId, commandName, targetLabel);
            if (review == null || origin == null) {
                throw new IllegalArgumentException("sanction action state requires a case review");
            }
        }
    }

    record Review(
            UUID viewerId,
            String commandName,
            String targetLabel,
            Optional<Cases> origin,
            CaseReview caseReview,
            SanctionChangeAction action,
            Optional<Instant> replacementExpiration,
            String reason,
            UUID operationId
    ) implements SanctionChangeGuiState {
        public Review {
            validate(viewerId, commandName, targetLabel);
            if (origin == null || caseReview == null || action == null || replacementExpiration == null
                    || reason == null || reason.isBlank() || operationId == null) {
                throw new IllegalArgumentException("sanction change review fields must be present");
            }
            reason = reason.trim();
            if (reason.length() > 2_000) {
                throw new IllegalArgumentException("sanction change reason exceeds 2000 characters");
            }
            boolean needsExpiration = action == SanctionChangeAction.REDUCE_DURATION
                    || action == SanctionChangeAction.REPLACE_EXPIRATION;
            if (needsExpiration != replacementExpiration.isPresent()) {
                throw new IllegalArgumentException("sanction change expiration does not match its action");
            }
        }
    }

    private static void validate(UUID viewerId, String commandName, String targetLabel) {
        if (viewerId == null || commandName == null || commandName.isBlank()
                || targetLabel == null || targetLabel.isBlank()) {
            throw new IllegalArgumentException("sanction change GUI state fields must be present");
        }
    }
}
