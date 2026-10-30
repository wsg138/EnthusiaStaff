package net.enthusia.staff.domain.sanction;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
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
        Optional<SanctionChangeExpectation> expectation,
        Optional<Set<SanctionType>> typeScope
) {
    public SanctionChangeRequest {
        if (idempotencyKey == null || caseId == null || actor == null || action == null
                || replacementExpiration == null || reason == null || reason.isBlank()
                || expectation == null || typeScope == null) {
            throw new IllegalArgumentException("sanction change fields must be present");
        }
        typeScope = typeScope.map(Set::copyOf);
        if (typeScope.isPresent()) {
            if (typeScope.orElseThrow().isEmpty()
                    || (action != SanctionChangeAction.END_EARLY && action != SanctionChangeAction.REVOKE)) {
                throw new IllegalArgumentException(
                        "sanction type scope is only valid for end-early and revoke");
            }
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
            String reason,
            Optional<SanctionChangeExpectation> expectation
    ) {
        this(idempotencyKey, caseId, actor, action, replacementExpiration,
                reason, expectation, Optional.empty());
    }

    public SanctionChangeRequest(
            IdempotencyKey idempotencyKey,
            CaseId caseId,
            Actor actor,
            SanctionChangeAction action,
            Optional<Instant> replacementExpiration,
            String reason
    ) {
        this(idempotencyKey, caseId, actor, action, replacementExpiration,
                reason, Optional.empty(), Optional.empty());
    }
}
