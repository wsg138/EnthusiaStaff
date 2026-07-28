package net.enthusia.staff.domain.casefile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.domain.sanction.SanctionChangeExpectation;

public record CaseReview(
        CaseId caseId,
        UUID targetId,
        UUID actorId,
        String actorName,
        String actorRank,
        String publicReason,
        String exactReasonId,
        String sanctionFamily,
        String internalExplanation,
        String configurationVersion,
        CaseVisibility visibility,
        CaseState state,
        Instant issuedAt,
        long revision,
        Optional<PunishmentStepReview> punishmentStep,
        List<SanctionReview> sanctions,
        Optional<OverturnRequestReview> openOverturnRequest
) {
    public CaseReview {
        if (caseId == null || targetId == null || actorId == null || visibility == null
                || state == null || issuedAt == null || revision < 0 || punishmentStep == null
                || sanctions == null || openOverturnRequest == null) {
            throw new IllegalArgumentException("case review fields must be present");
        }
        actorName = Checks.nonBlank(actorName, "actorName", 64);
        actorRank = Checks.nonBlank(actorRank, "actorRank", 32);
        publicReason = Checks.nonBlank(publicReason, "publicReason", 160);
        exactReasonId = Checks.nonBlank(exactReasonId, "exactReasonId", 96);
        sanctionFamily = Checks.nonBlank(sanctionFamily, "sanctionFamily", 64);
        if (internalExplanation == null) {
            throw new IllegalArgumentException("internalExplanation must be present");
        }
        internalExplanation = internalExplanation.trim();
        configurationVersion = Checks.nonBlank(configurationVersion, "configurationVersion", 128);
        sanctions = List.copyOf(sanctions);
    }

    public boolean hasActiveSanctions() {
        return sanctions.stream().anyMatch(SanctionReview::active);
    }

    public SanctionChangeExpectation changeExpectation() {
        return new SanctionChangeExpectation(
                revision,
                sanctions.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        SanctionReview::sanctionId,
                        SanctionReview::revision
                )),
                punishmentStep.map(PunishmentStepReview::escalationContributes),
                openOverturnRequest.map(OverturnRequestReview::requestId)
        );
    }
}
