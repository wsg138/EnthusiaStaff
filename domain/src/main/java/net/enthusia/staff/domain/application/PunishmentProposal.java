package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentProposal(
        UUID targetId,
        Actor requester,
        String reasonId,
        String family,
        String publicReason,
        String internalExplanation,
        String configurationVersion,
        CaseVisibility visibility,
        StaffRank requiredRank,
        EscalationDecision escalation,
        List<SanctionSpec> sanctions
) {
    public PunishmentProposal {
        if (targetId == null || requester == null || visibility == null || requiredRank == null
                || escalation == null || sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("punishment proposal fields must be present");
        }
        reasonId = Checks.nonBlank(reasonId, "reasonId", 96);
        family = Checks.nonBlank(family, "family", 96);
        publicReason = Checks.nonBlank(publicReason, "publicReason", 500);
        internalExplanation = Checks.nonBlank(internalExplanation, "internalExplanation", 4_000);
        configurationVersion = Checks.nonBlank(configurationVersion, "configurationVersion", 128);
        if (requiredRank == StaffRank.DEVELOPER || requiredRank == StaffRank.SYSTEM) {
            throw new IllegalArgumentException("punishment proposal requires a moderation approval rank");
        }
        sanctions = List.copyOf(sanctions);
    }

    public static PunishmentProposal from(
            CreatePunishmentRequest request,
            PunishmentAssessment assessment
    ) {
        if (request == null || assessment == null) {
            throw new IllegalArgumentException("request and assessment must be present");
        }
        return new PunishmentProposal(
                request.targetId(),
                request.actor(),
                assessment.policy().id(),
                assessment.policy().family(),
                assessment.policy().publicReason(),
                request.internalExplanation(),
                assessment.configurationVersion(),
                request.visibility(),
                assessment.policy().requiredRank(),
                assessment.escalation(),
                assessment.sanctions()
        );
    }

    public PunishmentMatchKey matchKey() {
        return PunishmentMatchKey.of(targetId, reasonId, sanctions);
    }

    public PunishmentPlan toPlan(CaseId caseId, IdempotencyKey idempotencyKey, Instant issuedAt) {
        return new PunishmentPlan(
                caseId,
                idempotencyKey,
                targetId,
                requester,
                reasonId,
                family,
                publicReason,
                internalExplanation,
                configurationVersion,
                visibility,
                issuedAt,
                escalation,
                sanctions
        );
    }
}
