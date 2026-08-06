package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.domain.website.WebsiteAppealCandidate;
import net.enthusia.staff.domain.website.WebsiteAppealDecisionPreparation;
import net.enthusia.staff.domain.website.WebsiteAppealPage;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;

public interface WebsiteModerationStore {
    String APPEAL_WORKFLOW_UNAVAILABLE = "Website appeal workflow is unavailable";

    PublicPunishmentPage listPublic(
            PublicPunishmentFilter filter,
            Optional<String> cursor,
            int limit,
            Instant now
    );

    List<PublicPunishment> searchPublic(String query, int limit, Instant now);

    Optional<PublicPunishment> publicCase(CaseId caseId, Instant now);

    PunishmentCodeBinding claimCode(String code, String accountId, String username, Instant now);

    PunishmentCodeBinding revalidateCode(
            UUID punishmentId,
            int codeGeneration,
            String accountId,
            Instant now
    );

    Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now);

    List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now);

    int ensureEligibleCodes(Instant now, int limit);

    PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now);

    boolean revokeCode(UUID punishmentId, UUID actorId, Instant now);

    boolean recordApiNonce(byte[] nonceHash, Instant expiresAt);

    int purgeExpiredApiNonces(Instant now, int limit);

    AppealAcceptancePreparation prepareAppealAcceptance(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            String accountId,
            String idempotencyKey,
            Instant now
    );

    void completeAppealAcceptance(UUID appealId, String state, String outcomeCode, Instant now);

    default List<WebsiteAppealCandidate> eligibleAppeals(
            String accountId,
            int limit,
            Instant now
    ) {
        throw unavailableAppealWorkflow();
    }

    default WebsiteAppealSubmission submitAppeal(
            UUID punishmentId,
            String accountId,
            String username,
            String reason,
            String idempotencyKey,
            Instant now
    ) {
        throw unavailableAppealWorkflow();
    }

    default WebsiteAppealPage listAppeals(
            String state,
            Optional<String> cursor,
            int limit,
            Instant now
    ) {
        throw unavailableAppealWorkflow();
    }

    default WebsiteAppealDecisionPreparation prepareAppealDecision(
            UUID appealId,
            long expectedVersion,
            String decision,
            String note,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) {
        throw unavailableAppealWorkflow();
    }

    private static UnsupportedOperationException unavailableAppealWorkflow() {
        return new UnsupportedOperationException(APPEAL_WORKFLOW_UNAVAILABLE);
    }
}
