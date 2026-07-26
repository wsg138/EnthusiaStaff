package net.enthusia.staff.domain.application;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record CreatePunishmentRequest(
        IdempotencyKey idempotencyKey,
        UUID targetId,
        Actor actor,
        String reasonId,
        String internalExplanation,
        CaseVisibility visibility,
        List<SanctionSpec> overrideSanctions
) {
    public CreatePunishmentRequest {
        if (idempotencyKey == null || targetId == null || actor == null || reasonId == null
                || internalExplanation == null || visibility == null || overrideSanctions == null) {
            throw new IllegalArgumentException("punishment request fields must be present");
        }
        reasonId = reasonId.trim();
        internalExplanation = internalExplanation.trim();
        if (reasonId.isBlank() || reasonId.length() > 96) {
            throw new IllegalArgumentException("invalid reasonId");
        }
        if (internalExplanation.length() > 4_000) {
            throw new IllegalArgumentException("internal explanation exceeds 4000 characters");
        }
        overrideSanctions = List.copyOf(overrideSanctions);
    }

    public boolean usesOverride() {
        return !overrideSanctions.isEmpty();
    }
}
