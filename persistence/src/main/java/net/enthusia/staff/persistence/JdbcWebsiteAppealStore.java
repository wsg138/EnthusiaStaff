package net.enthusia.staff.persistence;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.JdbcPunishmentCodeRepository.CodeRow;
import net.enthusia.staff.persistence.JdbcWebsiteAppealRepository.AppealRow;

final class JdbcWebsiteAppealStore {
    private static final String APPEAL_PREPARED = "PREPARED";
    private static final String APPEAL_APPLIED = "APPLIED";
    private static final String APPEAL_REJECTED = "REJECTED";
    private static final String ELIGIBLE = "ELIGIBLE";

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;
    private final JdbcPunishmentCodeRepository punishmentCodes;
    private final JdbcWebsiteAppealRepository appeals;

    JdbcWebsiteAppealStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            JdbcPunishmentCodeRepository punishmentCodes
    ) {
        if (dataSource == null || codeProtector == null || punishmentCodes == null) {
            throw new IllegalArgumentException("Website appeal store dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
        this.punishmentCodes = punishmentCodes;
        this.appeals = new JdbcWebsiteAppealRepository();
    }

    AppealAcceptancePreparation prepare(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            String accountId,
            String idempotencyKey,
            Instant now
    ) {
        validatePreparation(appealId, punishmentId, caseId, idempotencyKey, now);
        byte[] accountToken = accountToken(accountId);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to prepare appeal acceptance",
                connection -> prepare(
                        connection,
                        appealId,
                        punishmentId,
                        caseId,
                        accountToken,
                        idempotencyKey,
                        now
                )
        );
    }

    void complete(UUID appealId, String state, String outcomeCode, Instant now) {
        validateCompletion(appealId, state, outcomeCode, now);
        JdbcTransactionSupport.execute(
                dataSource,
                "Unable to complete appeal acceptance",
                connection -> {
                    complete(connection, appealId, state, outcomeCode, now);
                    return null;
                }
        );
    }

    private AppealAcceptancePreparation prepare(
            Connection connection,
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        CodeRow code = punishmentCodes.selectCodeBySanction(connection, punishmentId, true);
        List<AppealRow> existing = appeals.selectCandidates(
                connection,
                appealId,
                punishmentId,
                idempotencyKey,
                true
        );
        if (!existing.isEmpty()) {
            return existingResult(
                    existing,
                    appealId,
                    punishmentId,
                    caseId,
                    accountToken,
                    idempotencyKey
            );
        }
        AppealAcceptancePreparation.Rejected rejection = bindingRejection(
                code,
                caseId,
                accountToken,
                now
        );
        if (rejection != null) {
            return rejection;
        }
        return insertPreparation(
                connection,
                appealId,
                punishmentId,
                caseId,
                accountToken,
                idempotencyKey,
                now
        );
    }

    private AppealAcceptancePreparation insertPreparation(
            Connection connection,
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        try {
            appeals.insert(
                    connection,
                    appealId,
                    punishmentId,
                    caseId,
                    accountToken,
                    idempotencyKey,
                    now
            );
            return new AppealAcceptancePreparation.Ready(false);
        } catch (SQLException exception) {
            if (!JdbcWebsiteAppealRepository.isDuplicateKey(exception)) {
                throw exception;
            }
            List<AppealRow> concurrent = appeals.selectCandidates(
                    connection,
                    appealId,
                    punishmentId,
                    idempotencyKey,
                    true
            );
            if (concurrent.isEmpty()) {
                throw exception;
            }
            return existingResult(
                    concurrent,
                    appealId,
                    punishmentId,
                    caseId,
                    accountToken,
                    idempotencyKey
            );
        }
    }

    private static AppealAcceptancePreparation existingResult(
            List<AppealRow> candidates,
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey
    ) {
        if (candidates.size() != 1 || !matches(
                candidates.getFirst(),
                appealId,
                punishmentId,
                caseId,
                accountToken,
                idempotencyKey
        )) {
            throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The appeal request conflicts with prior state");
        }
        AppealRow existing = candidates.getFirst();
        if (APPEAL_REJECTED.equals(existing.state())) {
            return new AppealAcceptancePreparation.Rejected(
                    existing.outcomeCode() == null ? "APPEAL_REJECTED" : existing.outcomeCode(),
                    "The appeal acceptance was previously rejected"
            );
        }
        return new AppealAcceptancePreparation.Ready(true);
    }

    private static AppealAcceptancePreparation.Rejected bindingRejection(
            CodeRow code,
            CaseId caseId,
            byte[] accountToken,
            Instant now
    ) {
        if (code == null || !code.caseId().equals(caseId)) {
            return rejected("PUNISHMENT_NOT_FOUND", "The punishment could not be found");
        }
        if (code.claimedAccountToken() == null
                || !MessageDigest.isEqual(code.claimedAccountToken(), accountToken)) {
            return rejected("BINDING_ACCOUNT_MISMATCH", "The appeal is not bound to this punishment");
        }
        if (!ELIGIBLE.equals(eligibility(code, now))) {
            return rejected("PUNISHMENT_INELIGIBLE", "That punishment is no longer active");
        }
        return null;
    }

    private void complete(
            Connection connection,
            UUID appealId,
            String state,
            String outcomeCode,
            Instant now
    ) throws SQLException {
        AppealRow existing = appeals.selectById(connection, appealId, true);
        if (existing == null) {
            throw notFound("APPEAL_NOT_FOUND", "The appeal request could not be found");
        }
        if (!APPEAL_PREPARED.equals(existing.state())) {
            if (state.equals(existing.state()) && outcomeCode.equals(existing.outcomeCode())) {
                return;
            }
            throw conflict("APPEAL_STATE_CONFLICT", "The appeal completion conflicts with prior state");
        }
        appeals.complete(connection, appealId, state, outcomeCode, now);
    }

    private static boolean matches(
            AppealRow existing,
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey
    ) {
        return existing.appealId().equals(appealId)
                && existing.punishmentId().equals(punishmentId)
                && existing.caseId().equals(caseId)
                && MessageDigest.isEqual(existing.accountToken(), accountToken)
                && existing.idempotencyKey().equals(idempotencyKey);
    }

    private static String eligibility(CodeRow row, Instant now) {
        return WebsitePunishmentProjection.eligibilityState(
                row.codeStatus(),
                row.caseState(),
                row.sanctionStatus(),
                row.sanctionType(),
                row.expiration(),
                now
        );
    }

    private byte[] accountToken(String accountId) {
        try {
            return codeProtector.accountToken(accountId);
        } catch (IllegalArgumentException exception) {
            throw invalid("INVALID_ACCOUNT_ID", "The website account ID is invalid");
        }
    }

    private static void validatePreparation(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            String idempotencyKey,
            Instant now
    ) {
        if (appealId == null || punishmentId == null || caseId == null || now == null) {
            throw invalid("INVALID_APPEAL_ACCEPTANCE", "The appeal acceptance request is invalid");
        }
        validateIdempotencyKey(idempotencyKey);
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128
                || !idempotencyKey.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw invalid("INVALID_APPEAL_ACCEPTANCE", "The appeal acceptance request is invalid");
        }
    }

    private static void validateCompletion(
            UUID appealId,
            String state,
            String outcomeCode,
            Instant now
    ) {
        if (appealId == null || now == null || !List.of(APPEAL_APPLIED, APPEAL_REJECTED).contains(state)
                || outcomeCode == null || !outcomeCode.matches("[A-Z0-9_]{3,64}")) {
            throw invalid("INVALID_APPEAL_COMPLETION", "The appeal completion is invalid");
        }
    }

    private static AppealAcceptancePreparation.Rejected rejected(String code, String message) {
        return new AppealAcceptancePreparation.Rejected(code, message);
    }

    private static WebsiteModerationException invalid(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INVALID, code, message);
    }

    private static WebsiteModerationException notFound(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.NOT_FOUND, code, message);
    }

    private static WebsiteModerationException conflict(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.CONFLICT, code, message);
    }
}
