package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.domain.website.WebsiteModerationException;
import net.enthusia.staff.persistence.JdbcPunishmentCodeRepository.CodeRow;

public final class JdbcWebsiteModerationStore implements WebsiteModerationStore {
    private static final int MAX_BATCH = 5_000;
    private static final String APPEAL_APPLIED = "APPLIED";
    private static final String APPEAL_REJECTED = "REJECTED";

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;
    private final JdbcPublicPunishmentRegistry publicRegistry;
    private final JdbcPunishmentCodeStore punishmentCodes;
    private final JdbcPunishmentCodeRepository punishmentCodeRepository;

    public JdbcWebsiteModerationStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            ObjectMapper json
    ) {
        if (dataSource == null || codeProtector == null || json == null) {
            throw new IllegalArgumentException("Website moderation store dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
        this.publicRegistry = new JdbcPublicPunishmentRegistry(dataSource);
        this.punishmentCodeRepository = new JdbcPunishmentCodeRepository();
        this.punishmentCodes = new JdbcPunishmentCodeStore(
                dataSource,
                codeProtector,
                new JdbcWebsiteAuditWriter(json)
        );
    }

    @Override
    public PublicPunishmentPage listPublic(
            PublicPunishmentFilter filter,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        return publicRegistry.listPublic(filter, encodedCursor, limit, now);
    }

    @Override
    public List<PublicPunishment> searchPublic(String query, int limit, Instant now) {
        return publicRegistry.searchPublic(query, limit, now);
    }

    @Override
    public Optional<PublicPunishment> publicCase(CaseId caseId, Instant now) {
        return publicRegistry.publicCase(caseId, now);
    }

    @Override
    public PunishmentCodeBinding claimCode(String code, String accountId, String username, Instant now) {
        return punishmentCodes.claimCode(code, accountId, username, now);
    }

    @Override
    public PunishmentCodeBinding revalidateCode(
            UUID punishmentId,
            int codeGeneration,
            String accountId,
            Instant now
    ) {
        return punishmentCodes.revalidateCode(punishmentId, codeGeneration, accountId, now);
    }

    @Override
    public Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now) {
        return punishmentCodes.codeForSanction(punishmentId, now);
    }

    @Override
    public List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now) {
        return punishmentCodes.codesForCase(caseId, now);
    }

    @Override
    public int ensureEligibleCodes(Instant now, int limit) {
        return punishmentCodes.ensureEligibleCodes(now, limit);
    }

    @Override
    public PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now) {
        return punishmentCodes.rotateCode(punishmentId, actorId, now);
    }

    @Override
    public boolean revokeCode(UUID punishmentId, UUID actorId, Instant now) {
        return punishmentCodes.revokeCode(punishmentId, actorId, now);
    }

    @Override
    public boolean recordApiNonce(byte[] nonceHash, Instant expiresAt) {
        if (nonceHash == null || nonceHash.length != 32 || expiresAt == null) {
            throw invalid("INVALID_NONCE", "The API nonce is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO website_api_nonces(nonce_hash, expires_at) VALUES (?, ?)
                     """)) {
            statement.setBytes(1, nonceHash.clone());
            statement.setTimestamp(2, Timestamp.from(expiresAt));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistence("Unable to record the API nonce", exception);
        }
    }

    @Override
    public int purgeExpiredApiNonces(Instant now, int limit) {
        if (now == null || limit < 1 || limit > MAX_BATCH) {
            throw invalid("INVALID_NONCE_BATCH", "The API nonce cleanup batch is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM website_api_nonces
                     WHERE expires_at <= ?
                     ORDER BY expires_at
                     LIMIT ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw persistence("Unable to purge expired API nonces", exception);
        }
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
        if (appealId == null || punishmentId == null || caseId == null || now == null
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128
                || !idempotencyKey.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw invalid("INVALID_APPEAL_ACCEPTANCE", "The appeal acceptance request is invalid");
        }
        byte[] accountToken = accountToken(accountId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ExistingAppeal existing = selectExistingAppeal(
                        connection, appealId, idempotencyKey, true
                );
                if (existing != null) {
                    if (!existing.matches(appealId, punishmentId, caseId, accountToken, idempotencyKey)) {
                        connection.rollback();
                        throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The appeal request conflicts with prior state");
                    }
                    connection.rollback();
                    if (APPEAL_REJECTED.equals(existing.state())) {
                        return new AppealAcceptancePreparation.Rejected(
                                existing.outcomeCode() == null ? "APPEAL_REJECTED" : existing.outcomeCode(),
                                "The appeal acceptance was previously rejected"
                        );
                    }
                    return new AppealAcceptancePreparation.Ready(true);
                }
                CodeRow row = punishmentCodeRepository.selectCodeBySanction(
                        connection,
                        punishmentId,
                        true
                );
                if (row == null || !row.caseId().equals(caseId)) {
                    connection.rollback();
                    return new AppealAcceptancePreparation.Rejected(
                            "PUNISHMENT_NOT_FOUND", "The punishment could not be found"
                    );
                }
                if (row.claimedAccountToken() == null
                        || !MessageDigest.isEqual(row.claimedAccountToken(), accountToken)) {
                    connection.rollback();
                    return new AppealAcceptancePreparation.Rejected(
                            "BINDING_ACCOUNT_MISMATCH", "The appeal is not bound to this punishment"
                    );
                }
                String eligibility = eligibility(row, now);
                if (!"ELIGIBLE".equals(eligibility)) {
                    connection.rollback();
                    return new AppealAcceptancePreparation.Rejected(
                            "PUNISHMENT_INELIGIBLE", "That punishment is no longer active"
                    );
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO website_appeal_requests(
                            appeal_id, punishment_id, case_id, player_account_token,
                            idempotency_key, state, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'PREPARED', ?, ?)
                        """)) {
                    statement.setBytes(1, UuidBytes.toBytes(appealId));
                    statement.setBytes(2, UuidBytes.toBytes(punishmentId));
                    statement.setString(3, caseId.value());
                    statement.setBytes(4, accountToken);
                    statement.setString(5, idempotencyKey);
                    statement.setTimestamp(6, Timestamp.from(now));
                    statement.setTimestamp(7, Timestamp.from(now));
                    statement.executeUpdate();
                }
                connection.commit();
                return new AppealAcceptancePreparation.Ready(false);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to prepare appeal acceptance", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open an appeal transaction", exception);
        }
    }

    @Override
    public void completeAppealAcceptance(UUID appealId, String state, String outcomeCode, Instant now) {
        if (appealId == null || now == null || !List.of(APPEAL_APPLIED, APPEAL_REJECTED).contains(state)
                || outcomeCode == null || !outcomeCode.matches("[A-Z0-9_]{3,64}")) {
            throw invalid("INVALID_APPEAL_COMPLETION", "The appeal completion is invalid");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String existingState;
                String existingOutcome;
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT state, outcome_code
                        FROM website_appeal_requests
                        WHERE appeal_id = ?
                        FOR UPDATE
                        """)) {
                    select.setBytes(1, UuidBytes.toBytes(appealId));
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            throw notFound("APPEAL_NOT_FOUND", "The appeal request could not be found");
                        }
                        existingState = result.getString("state");
                        existingOutcome = result.getString("outcome_code");
                    }
                }
                if (!"PREPARED".equals(existingState)) {
                    connection.rollback();
                    if (state.equals(existingState) && outcomeCode.equals(existingOutcome)) {
                        return;
                    }
                    throw conflict("APPEAL_STATE_CONFLICT", "The appeal completion conflicts with prior state");
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE website_appeal_requests
                        SET state = ?, outcome_code = ?, updated_at = ?
                        WHERE appeal_id = ? AND state = 'PREPARED'
                        """)) {
                    update.setString(1, state);
                    update.setString(2, outcomeCode);
                    update.setTimestamp(3, Timestamp.from(now));
                    update.setBytes(4, UuidBytes.toBytes(appealId));
                    JdbcTransactionSupport.requireSingleUpdate(
                            update.executeUpdate(),
                            "Appeal state changed during completion"
                    );
                }
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to complete appeal acceptance", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open an appeal transaction", exception);
        }
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

    private static ExistingAppeal selectExistingAppeal(
            Connection connection,
            UUID appealId,
            String idempotencyKey,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT appeal_id, punishment_id, case_id, player_account_token,
                       idempotency_key, state, outcome_code
                FROM website_appeal_requests
                WHERE appeal_id = ? OR idempotency_key = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            statement.setString(2, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                ExistingAppeal found = null;
                while (result.next()) {
                    ExistingAppeal current = readExistingAppeal(result);
                    if (found != null) {
                        throw new SQLException("Appeal ID and idempotency key identify different requests");
                    }
                    found = current;
                }
                return found;
            }
        }
    }

    private static ExistingAppeal readExistingAppeal(ResultSet result) throws SQLException {
        return new ExistingAppeal(
                UuidBytes.fromBytes(result.getBytes("appeal_id")),
                UuidBytes.fromBytes(result.getBytes("punishment_id")),
                new CaseId(result.getString("case_id")),
                result.getBytes("player_account_token"),
                result.getString("idempotency_key"),
                result.getString("state"),
                result.getString("outcome_code")
        );
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Closing returns the connection to the pool; the original result remains authoritative.
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

    private static ModerationPersistenceException persistence(String message, Exception exception) {
        return exception instanceof ModerationPersistenceException persistenceException
                ? persistenceException
                : new ModerationPersistenceException(message, exception);
    }

    private record ExistingAppeal(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String idempotencyKey,
            String state,
            String outcomeCode
    ) {
        private boolean matches(
                UUID expectedAppeal,
                UUID expectedPunishment,
                CaseId expectedCase,
                byte[] expectedAccount,
                String expectedIdempotency
        ) {
            return appealId.equals(expectedAppeal)
                    && punishmentId.equals(expectedPunishment)
                    && caseId.equals(expectedCase)
                    && MessageDigest.isEqual(accountToken, expectedAccount)
                    && idempotencyKey.equals(expectedIdempotency);
        }
    }
}
