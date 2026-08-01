package net.enthusia.staff.persistence;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.JdbcPunishmentCodeRepository.CodeRecord;
import net.enthusia.staff.persistence.JdbcPunishmentCodeRepository.CodeRow;
import net.enthusia.staff.persistence.JdbcPunishmentCodeRepository.SanctionRow;

final class JdbcPunishmentCodeStore {
    private static final int MAX_BATCH = 5_000;
    private static final String ACTIVE = "ACTIVE";
    private static final String ELIGIBLE = "ELIGIBLE";

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;
    private final JdbcPunishmentCodeRepository repository;
    private final JdbcWebsiteAuditWriter auditWriter;

    JdbcPunishmentCodeStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            JdbcWebsiteAuditWriter auditWriter
    ) {
        if (dataSource == null || codeProtector == null || auditWriter == null) {
            throw new IllegalArgumentException("Punishment-code store dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
        this.auditWriter = auditWriter;
        this.repository = new JdbcPunishmentCodeRepository();
    }

    PunishmentCodeBinding claimCode(String code, String accountId, String username, Instant now) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}") || now == null) {
            throw invalid("INVALID_CODE_CLAIM", "The punishment-code claim is invalid");
        }
        byte[] codeHash = codeHash(code);
        byte[] accountToken = accountToken(accountId);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to claim the punishment code",
                connection -> claimCode(connection, codeHash, accountToken, username, now)
        );
    }

    PunishmentCodeBinding revalidateCode(
            UUID punishmentId,
            int codeGeneration,
            String accountId,
            Instant now
    ) {
        validateRevalidation(punishmentId, codeGeneration, now);
        byte[] accountToken = accountToken(accountId);
        try (Connection connection = dataSource.getConnection()) {
            CodeRow row = repository.selectCodeBySanction(connection, punishmentId, false);
            if (row == null) {
                throw notFound("BINDING_NOT_FOUND", "The punishment binding could not be found");
            }
            if (row.generation() != codeGeneration) {
                return binding(row, "CODE_ROTATED", requiredUsername(row.currentUsername()));
            }
            requireMatchingAccount(row, accountToken);
            return binding(row, eligibility(row, now), requiredUsername(row.currentUsername()));
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to revalidate the punishment binding", exception);
        }
    }

    Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now) {
        if (punishmentId == null || now == null) {
            throw invalid("INVALID_PUNISHMENT", "The punishment ID is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to obtain the punishment code",
                connection -> codeForSanction(connection, punishmentId, now)
        );
    }

    List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now) {
        if (caseId == null || now == null) {
            throw invalid("INVALID_CASE_ID", "The case ID is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to read punishment codes for the case",
                connection -> codesForCase(connection, caseId, now)
        );
    }

    int ensureEligibleCodes(Instant now, int limit) {
        if (now == null || limit < 1 || limit > MAX_BATCH) {
            throw invalid("INVALID_CODE_BATCH", "The punishment-code batch is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to backfill punishment codes",
                connection -> ensureEligibleCodes(connection, now, limit)
        );
    }

    PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now) {
        if (punishmentId == null || actorId == null || now == null) {
            throw invalid("INVALID_CODE_ROTATION", "The code rotation request is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to rotate the punishment code",
                connection -> rotateCode(connection, punishmentId, actorId, now)
        );
    }

    boolean revokeCode(UUID punishmentId, UUID actorId, Instant now) {
        if (punishmentId == null || actorId == null || now == null) {
            throw invalid("INVALID_CODE_REVOCATION", "The code revocation request is invalid");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to revoke the punishment code",
                connection -> revokeCode(connection, punishmentId, actorId, now)
        );
    }

    private PunishmentCodeBinding claimCode(
            Connection connection,
            byte[] codeHash,
            byte[] accountToken,
            String username,
            Instant now
    ) throws SQLException {
        CodeRow row = repository.selectCodeByHash(
                connection,
                codeProtector.keyVersion(),
                codeHash,
                true
        );
        if (row == null || !repository.usernameMatches(connection, row.targetId(), username)) {
            throw notFound("PUNISHMENT_CODE_INVALID", "The punishment code could not be verified");
        }
        String eligibility = eligibility(row, now);
        if (!ELIGIBLE.equals(eligibility)) {
            throw ineligible("PUNISHMENT_INELIGIBLE", "That punishment is not eligible for an appeal");
        }
        boolean firstClaim = row.claimedAccountToken() == null;
        if (!firstClaim && !MessageDigest.isEqual(row.claimedAccountToken(), accountToken)) {
            throw conflict("PUNISHMENT_ALREADY_BOUND", "That punishment is already bound");
        }
        if (firstClaim) {
            persistFirstClaim(connection, row, accountToken, now);
        }
        return binding(row, eligibility, username);
    }

    private void persistFirstClaim(
            Connection connection,
            CodeRow row,
            byte[] accountToken,
            Instant now
    ) throws SQLException {
        if (!JdbcTransactionSupport.updatedOne(
                repository.claimCode(connection, row.sanctionId(), accountToken, now))) {
            throw conflict("PUNISHMENT_ALREADY_BOUND", "That punishment is already bound");
        }
        auditWriter.write(
                connection,
                "PUNISHMENT_CODE_CLAIMED",
                null,
                row.targetId(),
                row.caseId(),
                Map.of("punishmentId", row.sanctionId().toString(), "firstClaim", true),
                now
        );
    }

    private Optional<PunishmentCodeDisplay> codeForSanction(
            Connection connection,
            UUID punishmentId,
            Instant now
    ) throws SQLException {
        SanctionRow sanction = repository.selectSanction(connection, punishmentId, true);
        if (sanction == null || !eligibleSanction(sanction, now)) {
            return Optional.empty();
        }
        CodeRecord code = existingOrCreateCode(connection, sanction, now);
        return ACTIVE.equals(code.status())
                ? Optional.of(display(sanction, code))
                : Optional.empty();
    }

    private List<PunishmentCodeDisplay> codesForCase(
            Connection connection,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        List<PunishmentCodeDisplay> displays = new ArrayList<>();
        for (SanctionRow sanction : repository.selectSanctionsForCase(connection, caseId)) {
            if (!eligibleSanction(sanction, now)) {
                continue;
            }
            CodeRecord code = existingOrCreateCode(connection, sanction, now);
            if (ACTIVE.equals(code.status())) {
                displays.add(display(sanction, code));
            }
        }
        return List.copyOf(displays);
    }

    private int ensureEligibleCodes(Connection connection, Instant now, int limit) throws SQLException {
        int inserted = 0;
        for (SanctionRow sanction : repository.selectEligibleWithoutCodes(connection, now, limit)) {
            String derived = codeProtector.code(sanction.sanctionId(), 1);
            byte[] hash = codeProtector.verificationHash(derived);
            int changed = repository.insertCodeIfMissing(
                    connection,
                    sanction,
                    codeProtector.keyVersion(),
                    hash,
                    now
            );
            if (JdbcTransactionSupport.updatedOne(changed)) {
                inserted++;
            } else if (!repository.codeExists(connection, sanction.sanctionId())) {
                throw new SQLException("Punishment code hash collision detected");
            }
        }
        return inserted;
    }

    private PunishmentCodeDisplay rotateCode(
            Connection connection,
            UUID punishmentId,
            UUID actorId,
            Instant now
    ) throws SQLException {
        SanctionRow sanction = repository.selectSanction(connection, punishmentId, true);
        if (sanction == null) {
            throw notFound("PUNISHMENT_NOT_FOUND", "The punishment could not be found");
        }
        if (!eligibleSanction(sanction, now)) {
            throw ineligible("PUNISHMENT_INELIGIBLE", "That punishment is not eligible for a code");
        }
        CodeRecord existing = repository.selectCodeRecord(connection, punishmentId, true);
        int generation = nextGeneration(existing);
        String derived = codeProtector.code(punishmentId, generation);
        byte[] hash = codeProtector.verificationHash(derived);
        persistRotation(connection, sanction, existing, generation, hash, actorId, now);
        auditWriter.write(
                connection,
                "PUNISHMENT_CODE_ROTATED",
                actorId,
                sanction.targetId(),
                sanction.caseId(),
                Map.of("punishmentId", punishmentId.toString(), "generation", generation),
                now
        );
        return new PunishmentCodeDisplay(
                punishmentId,
                sanction.caseId(),
                generation,
                WebsitePunishmentProjection.publicType(sanction.sanctionType()),
                derived
        );
    }

    private void persistRotation(
            Connection connection,
            SanctionRow sanction,
            CodeRecord existing,
            int generation,
            byte[] hash,
            UUID actorId,
            Instant now
    ) throws SQLException {
        if (existing == null) {
            repository.insertCode(
                    connection,
                    sanction,
                    codeProtector.keyVersion(),
                    generation,
                    hash,
                    now,
                    actorId
            );
        } else {
            repository.rotateCode(
                    connection,
                    sanction.sanctionId(),
                    codeProtector.keyVersion(),
                    generation,
                    hash,
                    actorId,
                    now
            );
        }
    }

    private boolean revokeCode(
            Connection connection,
            UUID punishmentId,
            UUID actorId,
            Instant now
    ) throws SQLException {
        SanctionRow sanction = repository.selectSanction(connection, punishmentId, true);
        if (sanction == null) {
            throw notFound("PUNISHMENT_NOT_FOUND", "The punishment could not be found");
        }
        int changed = repository.revokeCode(connection, punishmentId, actorId, now);
        if (JdbcTransactionSupport.updatedOne(changed)) {
            auditWriter.write(
                    connection,
                    "PUNISHMENT_CODE_REVOKED",
                    actorId,
                    sanction.targetId(),
                    sanction.caseId(),
                    Map.of("punishmentId", punishmentId.toString()),
                    now
            );
            return true;
        }
        return false;
    }

    private CodeRecord existingOrCreateCode(
            Connection connection,
            SanctionRow sanction,
            Instant now
    ) throws SQLException {
        CodeRecord code = repository.selectCodeRecord(connection, sanction.sanctionId(), true);
        return code == null ? createCode(connection, sanction, 1, now, null) : code;
    }

    private CodeRecord createCode(
            Connection connection,
            SanctionRow sanction,
            int generation,
            Instant now,
            UUID actorId
    ) throws SQLException {
        String derived = codeProtector.code(sanction.sanctionId(), generation);
        byte[] hash = codeProtector.verificationHash(derived);
        repository.insertCode(
                connection,
                sanction,
                codeProtector.keyVersion(),
                generation,
                hash,
                now,
                actorId
        );
        return new CodeRecord(codeProtector.keyVersion(), generation, hash, ACTIVE);
    }

    private PunishmentCodeDisplay display(SanctionRow sanction, CodeRecord code) {
        if (code.keyVersion() != codeProtector.keyVersion()) {
            throw unavailable(
                    "PUNISHMENT_CODE_KEY_UNAVAILABLE",
                    "The punishment code uses an unavailable key version"
            );
        }
        String derived = codeProtector.code(sanction.sanctionId(), code.generation());
        if (!MessageDigest.isEqual(code.codeHash(), codeProtector.verificationHash(derived))) {
            throw unavailable("PUNISHMENT_CODE_INTEGRITY_FAILURE", "The punishment code failed integrity verification");
        }
        return new PunishmentCodeDisplay(
                sanction.sanctionId(),
                sanction.caseId(),
                code.generation(),
                WebsitePunishmentProjection.publicType(sanction.sanctionType()),
                derived
        );
    }

    private PunishmentCodeBinding binding(CodeRow row, String eligibility, String fallbackUsername) {
        String username = row.currentUsername();
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            username = requiredUsername(fallbackUsername);
        }
        return new PunishmentCodeBinding(
                row.sanctionId(),
                row.caseId(),
                row.generation(),
                WebsitePunishmentProjection.publicType(row.sanctionType()),
                username,
                ELIGIBLE.equals(eligibility),
                eligibility
        );
    }

    private static int nextGeneration(CodeRecord existing) {
        if (existing == null) {
            return 1;
        }
        try {
            return Math.addExact(existing.generation(), 1);
        } catch (ArithmeticException exception) {
            throw new ModerationPersistenceException(
                    "Unable to rotate the punishment code",
                    exception
            );
        }
    }

    private static void validateRevalidation(
            UUID punishmentId,
            int codeGeneration,
            Instant now
    ) {
        if (punishmentId == null || codeGeneration < 1 || now == null) {
            throw invalid("INVALID_BINDING", "The punishment binding is invalid");
        }
    }

    private static void requireMatchingAccount(CodeRow row, byte[] accountToken) {
        if (row.claimedAccountToken() == null
                || !MessageDigest.isEqual(row.claimedAccountToken(), accountToken)) {
            throw conflict("BINDING_ACCOUNT_MISMATCH", "The punishment binding belongs to another account");
        }
    }

    private static String requiredUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            throw notFound("PLAYER_IDENTITY_UNAVAILABLE", "The punishment player identity is unavailable");
        }
        return username;
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

    private static boolean eligibleSanction(SanctionRow row, Instant now) {
        return ELIGIBLE.equals(WebsitePunishmentProjection.eligibilityState(
                ACTIVE,
                row.caseState(),
                row.sanctionStatus(),
                row.sanctionType(),
                row.expiration(),
                now
        ));
    }

    private byte[] codeHash(String code) {
        try {
            return codeProtector.verificationHash(code);
        } catch (IllegalArgumentException exception) {
            throw notFound("PUNISHMENT_CODE_INVALID", "The punishment code could not be verified");
        }
    }

    private byte[] accountToken(String accountId) {
        try {
            return codeProtector.accountToken(accountId);
        } catch (IllegalArgumentException exception) {
            throw invalid("INVALID_ACCOUNT_ID", "The website account ID is invalid");
        }
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

    private static WebsiteModerationException ineligible(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INELIGIBLE, code, message);
    }

    private static WebsiteModerationException unavailable(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.UNAVAILABLE, code, message);
    }
}
