package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentPlan(
        CaseId caseId,
        IdempotencyKey idempotencyKey,
        UUID targetId,
        Actor actor,
        String reasonId,
        String family,
        String publicReason,
        String internalExplanation,
        String configurationVersion,
        CaseVisibility visibility,
        Instant issuedAt,
        EscalationDecision escalation,
        List<SanctionSpec> sanctions
) {
    public PunishmentPlan {
        if (caseId == null || idempotencyKey == null || targetId == null || actor == null
                || reasonId == null || family == null || publicReason == null || internalExplanation == null
                || configurationVersion == null || visibility == null || issuedAt == null || escalation == null
                || sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("punishment plan fields must be present");
        }
        sanctions = List.copyOf(sanctions);
    }
}
