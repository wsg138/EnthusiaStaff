package net.enthusia.staff.velocity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;

abstract class WebsiteModerationStoreStub implements WebsiteModerationStore {
    @Override
    public PublicPunishmentPage listPublic(
            PublicPunishmentFilter filter,
            Optional<String> cursor,
            int limit,
            Instant now
    ) {
        throw unsupported();
    }

    @Override
    public List<PublicPunishment> searchPublic(String query, int limit, Instant now) {
        throw unsupported();
    }

    @Override
    public Optional<PublicPunishment> publicCase(CaseId caseId, Instant now) {
        throw unsupported();
    }

    @Override
    public PunishmentCodeBinding claimCode(
            String code,
            String accountId,
            String username,
            Instant now
    ) {
        throw unsupported();
    }

    @Override
    public PunishmentCodeBinding revalidateCode(
            UUID punishmentId,
            int codeGeneration,
            String accountId,
            Instant now
    ) {
        throw unsupported();
    }

    @Override
    public Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now) {
        throw unsupported();
    }

    @Override
    public List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now) {
        throw unsupported();
    }

    @Override
    public int ensureEligibleCodes(Instant now, int limit) {
        throw unsupported();
    }

    @Override
    public PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now) {
        throw unsupported();
    }

    @Override
    public boolean revokeCode(UUID punishmentId, UUID actorId, Instant now) {
        throw unsupported();
    }

    @Override
    public boolean recordApiNonce(byte[] nonceHash, Instant expiresAt) {
        throw unsupported();
    }

    @Override
    public int purgeExpiredApiNonces(Instant now, int limit) {
        throw unsupported();
    }

    @Override
    public AppealAcceptancePreparation prepareAppealAcceptance(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            String accountId,
            String idempotencyKey,
            Instant now
    ) {
        throw unsupported();
    }

    @Override
    public void completeAppealAcceptance(
            UUID appealId,
            String state,
            String outcomeCode,
            Instant now
    ) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("The test did not configure this store operation");
    }
}
