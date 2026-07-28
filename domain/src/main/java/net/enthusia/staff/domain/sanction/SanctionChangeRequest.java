package net.enthusia.staff.domain.sanction;

import java.time.Instant;
import java.util.Optional;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;

public record SanctionChangeRequest(
        IdempotencyKey idempotencyKey,
        CaseId caseId,
        Actor actor,
        SanctionChangeAction action,
        Optional<Instant> replacementExpiration,
        String reason,
        Optional<SanctionChangeExpectation> expectation
) {
    public SanctionChangeRequest {
        if (idempotencyKey == null || caseId == null || actor == null || action == null
                || replacementExpiration == null || reason == null || reason.isBlank() || expectation == null) {
            throw new IllegalArgumentException("sanction change fields must be present");
        }
        reason = reason.trim();
        if (reason.length() > 2_000) {
            throw new IllegalArgumentException("sanction change reason is too long");
        }
        boolean needsExpiration = action == SanctionChangeAction.REDUCE_DURATION
                || action == SanctionChangeAction.REPLACE_EXPIRATION;
        if (needsExpiration != replacementExpiration.isPresent()) {
            throw new IllegalArgumentException("replacement expiration presence does not match the action");
        }
    }

    public SanctionChangeRequest(
            IdempotencyKey idempotencyKey,
            CaseId caseId,
            Actor actor,
            SanctionChangeAction action,
            Optional<Instant> replacementExpiration,
            String reason
    ) {
        this(idempotencyKey, caseId, actor, action, replacementExpiration, reason, Optional.empty());
    }
}
