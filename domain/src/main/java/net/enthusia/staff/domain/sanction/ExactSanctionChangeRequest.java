package net.enthusia.staff.domain.sanction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;

public record ExactSanctionChangeRequest(
        IdempotencyKey idempotencyKey,
        UUID sanctionId,
        Actor actor,
        SanctionChangeAction action,
        Optional<Instant> replacementExpiration,
        String reason,
        Optional<UUID> linkedAppealId,
        Optional<UUID> linkedPunishmentRequestId,
        String originRuntime,
        boolean bypassHierarchy
) {
    public ExactSanctionChangeRequest {
        if (idempotencyKey == null || sanctionId == null || actor == null || action == null
                || replacementExpiration == null || linkedAppealId == null
                || linkedPunishmentRequestId == null) {
            throw new IllegalArgumentException("exact sanction change fields must be present");
        }
        if (action != SanctionChangeAction.REDUCE_DURATION
                && action != SanctionChangeAction.END_EARLY
                && action != SanctionChangeAction.REVOKE
                && action != SanctionChangeAction.FULL_OVERTURN) {
            throw new IllegalArgumentException("action is not supported for an exact sanction change");
        }
        boolean needsExpiration = action == SanctionChangeAction.REDUCE_DURATION;
        if (needsExpiration != replacementExpiration.isPresent()) {
            throw new IllegalArgumentException("replacement expiration presence does not match the action");
        }
        if (linkedAppealId.isPresent() && action != SanctionChangeAction.FULL_OVERTURN) {
            throw new IllegalArgumentException("appeal linkage is only valid for overturns");
        }
        reason = Checks.nonBlank(reason, "reason", 2_000);
        originRuntime = Checks.nonBlank(originRuntime, "originRuntime", 64);
    }
}
