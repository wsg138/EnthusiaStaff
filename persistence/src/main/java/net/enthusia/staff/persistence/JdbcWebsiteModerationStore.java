package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.domain.website.WebsiteModerationException;

public final class JdbcWebsiteModerationStore implements WebsiteModerationStore {
    private static final int MAX_PUBLIC_LIMIT = 100;
    private static final int MAX_BATCH = 5_000;
    private static final String PUBLIC_TYPE_CONDITION = """
              AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN', 'MUTE', 'WARNING')
              AND s.status IN ('ACTIVE', 'APPLIED', 'EXPIRED', 'ENDED_EARLY', 'REVOKED')
              AND CHAR_LENGTH(p.current_username) BETWEEN 3 AND 16
              AND p.current_username REGEXP '^[A-Za-z0-9_]{3,16}$'
            """;
    private static final String PUBLIC_SELECT = """
            SELECT s.sanction_id, s.case_id, s.sanction_type, s.status, s.issued_at,
                   s.expiration_at, c.public_reason, c.sanction_family, p.current_username,
                   pc.status AS code_status
            FROM public_sanctions s
            JOIN public_cases c ON c.case_id = s.case_id
            JOIN public_player_names p ON p.player_id = s.target_id
            LEFT JOIN punishment_codes pc ON pc.sanction_id = s.sanction_id
            WHERE 1 = 1
            """ + PUBLIC_TYPE_CONDITION;
    private static final String CODE_ROW_SELECT = """
            SELECT pc.sanction_id, pc.case_id, pc.key_version, pc.generation,
                   pc.code_hash, pc.status AS code_status, pc.claimed_account_token,
                   s.target_id, s.sanction_type, s.status AS sanction_status,
                   s.expiration_at, c.state AS case_state, p.current_username
            FROM punishment_codes pc
            JOIN sanctions s ON s.sanction_id = pc.sanction_id
            JOIN cases c ON c.case_id = pc.case_id
            JOIN players p ON p.player_id = s.target_id
            """;

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;
    private final ObjectMapper json;

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
        this.json = json;
    }

    @Override
    public PublicPunishmentPage listPublic(
            PublicPunishmentFilter filter,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        if (filter == null || encodedCursor == null || limit < 1 || limit > MAX_PUBLIC_LIMIT || now == null) {
            throw invalid("INVALID_PUBLIC_QUERY", "The public punishment query is invalid");
        }
        Optional<WebsitePunishmentProjection.Cursor> cursor =
                WebsitePunishmentProjection.decodeCursor(encodedCursor);
        StringBuilder sql = new StringBuilder(PUBLIC_SELECT);
        sql.append(switch (filter) {
            case ALL -> "";
            case BAN -> " AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN')";
            case MUTE -> " AND s.sanction_type = 'MUTE'";
            case WARNING -> " AND s.sanction_type = 'WARNING'";
        });
        if (cursor.isPresent()) {
            sql.append(" AND (s.issued_at < ? OR (s.issued_at = ? AND s.sanction_id < ?))");
        }
        sql.append(" ORDER BY s.issued_at DESC, s.sanction_id DESC LIMIT ?");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (cursor.isPresent()) {
                WebsitePunishmentProjection.Cursor value = cursor.orElseThrow();
                Timestamp issuedAt = Timestamp.from(value.issuedAt());
                statement.setTimestamp(index++, issuedAt);
                statement.setTimestamp(index++, issuedAt);
                statement.setBytes(index++, UuidBytes.toBytes(value.sanctionId()));
            }
            statement.setInt(index, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<PublicRow> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(readPublicRow(result, now));
                }
                boolean more = rows.size() > limit;
                if (more) {
                    rows.removeLast();
                }
                Optional<String> next = more && !rows.isEmpty()
                        ? Optional.of(WebsitePunishmentProjection.encodeCursor(
                                rows.getLast().issuedAt(), rows.getLast().sanctionId()
                        ))
                        : Optional.empty();
                return new PublicPunishmentPage(
                        rows.stream().map(PublicRow::punishment).toList(),
                        next
                );
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw persistence("Unable to read the public punishment registry", exception);
        }
    }

    @Override
    public List<PublicPunishment> searchPublic(String query, int limit, Instant now) {
        if (query == null || limit < 1 || limit > MAX_PUBLIC_LIMIT || now == null) {
            throw invalid("INVALID_SEARCH", "The punishment search is invalid");
        }
        String normalized = query.trim();
        if (normalized.length() < 2 || normalized.length() > 80
                || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw invalid("INVALID_SEARCH", "Search for a username or case ID");
        }
        String sql = PUBLIC_SELECT + """
              AND (
                    c.case_id = ?
                    OR p.lowercase_username = ?
                    OR EXISTS (
                        SELECT 1 FROM public_player_name_history history
                        WHERE history.player_id = s.target_id
                          AND history.lowercase_username = ?
                    )
              )
            ORDER BY s.issued_at DESC, s.sanction_id DESC
            LIMIT ?
            """;
        String lower = normalized.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized.toUpperCase(Locale.ROOT));
            statement.setString(2, lower);
            statement.setString(3, lower);
            statement.setInt(4, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PublicPunishment> punishments = new ArrayList<>();
                while (result.next()) {
                    punishments.add(readPublicRow(result, now).punishment());
                }
                return List.copyOf(punishments);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw persistence("Unable to search the public punishment registry", exception);
        }
    }

    @Override
    public Optional<PublicPunishment> publicCase(CaseId caseId, Instant now) {
        if (caseId == null || now == null) {
            throw invalid("INVALID_CASE_ID", "The case ID is invalid");
        }
        String sql = PUBLIC_SELECT + """
              AND c.case_id = ?
            ORDER BY s.issued_at DESC, s.sanction_id DESC
            LIMIT 1
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(readPublicRow(result, now).punishment())
                        : Optional.empty();
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw persistence("Unable to read the public case", exception);
        }
    }

    @Override
    public PunishmentCodeBinding claimCode(String code, String accountId, String username, Instant now) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}") || now == null) {
            throw invalid("INVALID_CODE_CLAIM", "The punishment-code claim is invalid");
        }
        byte[] codeHash = codeHash(code);
        byte[] accountToken = accountToken(accountId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CodeRow row = selectCodeByHash(connection, codeHash, true);
                if (row == null || !usernameMatches(connection, row.targetId(), username)) {
                    connection.rollback();
                    throw notFound("PUNISHMENT_CODE_INVALID", "The punishment code could not be verified");
                }
                String eligibility = eligibility(row, now);
                if (!"ELIGIBLE".equals(eligibility)) {
                    connection.rollback();
                    throw ineligible("PUNISHMENT_INELIGIBLE", "That punishment is not eligible for an appeal");
                }
                boolean firstClaim = row.claimedAccountToken() == null;
                if (!firstClaim && !MessageDigest.isEqual(row.claimedAccountToken(), accountToken)) {
                    connection.rollback();
                    throw conflict("PUNISHMENT_ALREADY_BOUND", "That punishment is already bound");
                }
                if (firstClaim) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE punishment_codes
                            SET claimed_account_token = ?, claimed_at = ?
                            WHERE sanction_id = ? AND claimed_account_token IS NULL
                            """)) {
                        update.setBytes(1, accountToken);
                        update.setTimestamp(2, Timestamp.from(now));
                        update.setBytes(3, UuidBytes.toBytes(row.sanctionId()));
                        if (update.executeUpdate() != 1) {
                            connection.rollback();
                            throw conflict("PUNISHMENT_ALREADY_BOUND", "That punishment is already bound");
                        }
                    }
                    insertAudit(
                            connection,
                            "PUNISHMENT_CODE_CLAIMED",
                            null,
                            row.targetId(),
                            row.caseId(),
                            Map.of("punishmentId", row.sanctionId().toString(), "firstClaim", true),
                            now
                    );
                }
                connection.commit();
                return binding(row, eligibility, username);
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw persistence("Unable to claim the punishment code", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open a punishment-code transaction", exception);
        }
    }

    @Override
    public PunishmentCodeBinding revalidateCode(
            UUID punishmentId,
            int codeGeneration,
            String accountId,
            Instant now
    ) {
        if (punishmentId == null || codeGeneration < 1 || now == null) {
            throw invalid("INVALID_BINDING", "The punishment binding is invalid");
        }
        byte[] accountToken = accountToken(accountId);
        try (Connection connection = dataSource.getConnection()) {
            CodeRow row = selectCodeBySanction(connection, punishmentId, false);
            if (row == null) {
                throw notFound("BINDING_NOT_FOUND", "The punishment binding could not be found");
            }
            if (row.generation() != codeGeneration) {
                return binding(row, "CODE_ROTATED", requiredUsername(row.currentUsername()));
            }
            if (row.claimedAccountToken() == null
                    || !MessageDigest.isEqual(row.claimedAccountToken(), accountToken)) {
                throw conflict("BINDING_ACCOUNT_MISMATCH", "The punishment binding belongs to another account");
            }
            return binding(row, eligibility(row, now), requiredUsername(row.currentUsername()));
        } catch (SQLException exception) {
            throw persistence("Unable to revalidate the punishment binding", exception);
        }
    }

    @Override
    public Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now) {
        if (punishmentId == null || now == null) {
            throw invalid("INVALID_PUNISHMENT", "The punishment ID is invalid");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SanctionRow sanction = selectSanction(connection, punishmentId, true);
                if (sanction == null || !eligibleSanction(sanction, now)) {
                    connection.rollback();
                    return Optional.empty();
                }
                CodeRecord code = selectCodeRecord(connection, punishmentId, true);
                if (code == null) {
                    code = createCode(connection, sanction, 1, now, null);
                }
                if (!"ACTIVE".equals(code.status())) {
                    connection.rollback();
                    return Optional.empty();
                }
                PunishmentCodeDisplay display = display(sanction, code);
                connection.commit();
                return Optional.of(display);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to obtain the punishment code", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open a punishment-code transaction", exception);
        }
    }

    @Override
    public List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now) {
        if (caseId == null || now == null) {
            throw invalid("INVALID_CASE_ID", "The case ID is invalid");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<SanctionRow> sanctions = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT s.sanction_id, s.case_id, s.target_id, s.sanction_type,
                               s.status AS sanction_status, s.expiration_at, c.state AS case_state
                        FROM sanctions s
                        JOIN cases c ON c.case_id = s.case_id
                        WHERE s.case_id = ?
                        ORDER BY s.issued_at, s.sanction_id
                        FOR UPDATE
                        """)) {
                    statement.setString(1, caseId.value());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            sanctions.add(readSanction(result));
                        }
                    }
                }
                List<PunishmentCodeDisplay> displays = new ArrayList<>();
                for (SanctionRow sanction : sanctions) {
                    if (!eligibleSanction(sanction, now)) {
                        continue;
                    }
                    CodeRecord code = selectCodeRecord(connection, sanction.sanctionId(), true);
                    if (code == null) {
                        code = createCode(connection, sanction, 1, now, null);
                    }
                    if ("ACTIVE".equals(code.status())) {
                        displays.add(display(sanction, code));
                    }
                }
                connection.commit();
                return List.copyOf(displays);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to read punishment codes for the case", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open a punishment-code transaction", exception);
        }
    }

    @Override
    public int ensureEligibleCodes(Instant now, int limit) {
        if (now == null || limit < 1 || limit > MAX_BATCH) {
            throw invalid("INVALID_CODE_BATCH", "The punishment-code batch is invalid");
        }
        String sql = """
                SELECT s.sanction_id, s.case_id, s.target_id, s.sanction_type,
                       s.status AS sanction_status, s.expiration_at, c.state AS case_state
                FROM sanctions s
                JOIN cases c ON c.case_id = s.case_id
                WHERE s.status = 'ACTIVE'
                  AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                  AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN', 'MUTE')
                  AND c.state <> 'FULLY_OVERTURNED'
                  AND NOT EXISTS (
                      SELECT 1 FROM punishment_codes pc WHERE pc.sanction_id = s.sanction_id
                  )
                ORDER BY s.issued_at, s.sanction_id
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<SanctionRow> sanctions = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setInt(2, limit);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            sanctions.add(readSanction(result));
                        }
                    }
                }
                int inserted = 0;
                for (SanctionRow sanction : sanctions) {
                    String derived = codeProtector.code(sanction.sanctionId(), 1);
                    byte[] hash = codeProtector.verificationHash(derived);
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT IGNORE INTO punishment_codes(
                                sanction_id, case_id, key_version, generation, code_hash, status, created_at
                            ) VALUES (?, ?, ?, 1, ?, 'ACTIVE', ?)
                            """)) {
                        statement.setBytes(1, UuidBytes.toBytes(sanction.sanctionId()));
                        statement.setString(2, sanction.caseId().value());
                        statement.setInt(3, codeProtector.keyVersion());
                        statement.setBytes(4, hash);
                        statement.setTimestamp(5, Timestamp.from(now));
                        int changed = statement.executeUpdate();
                        if (changed == 1) {
                            inserted++;
                        } else if (!codeExists(connection, sanction.sanctionId())) {
                            throw new SQLException("Punishment code hash collision detected");
                        }
                    }
                }
                connection.commit();
                return inserted;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to backfill punishment codes", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open a punishment-code transaction", exception);
        }
    }

    @Override
    public PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now) {
        if (punishmentId == null || actorId == null || now == null) {
            throw invalid("INVALID_CODE_ROTATION", "The code rotation request is invalid");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SanctionRow sanction = selectSanction(connection, punishmentId, true);
                if (sanction == null) {
                    connection.rollback();
                    throw notFound("PUNISHMENT_NOT_FOUND", "The punishment could not be found");
                }
                if (!eligibleSanction(sanction, now)) {
                    connection.rollback();
                    throw ineligible("PUNISHMENT_INELIGIBLE", "That punishment is not eligible for a code");
                }
                CodeRecord existing = selectCodeRecord(connection, punishmentId, true);
                int generation = existing == null ? 1 : Math.addExact(existing.generation(), 1);
                String derived = codeProtector.code(punishmentId, generation);
                byte[] hash = codeProtector.verificationHash(derived);
                if (existing == null) {
                    insertCodeRow(connection, sanction, generation, hash, now, actorId);
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE punishment_codes
                            SET key_version = ?, generation = ?, code_hash = ?, status = 'ACTIVE',
                                claimed_account_token = NULL, claimed_at = NULL, rotated_at = ?,
                                rotated_by = ?, revoked_at = NULL, revoked_by = NULL
                            WHERE sanction_id = ?
                            """)) {
                        statement.setInt(1, codeProtector.keyVersion());
                        statement.setInt(2, generation);
                        statement.setBytes(3, hash);
                        statement.setTimestamp(4, Timestamp.from(now));
                        statement.setBytes(5, UuidBytes.toBytes(actorId));
                        statement.setBytes(6, UuidBytes.toBytes(punishmentId));
                        statement.executeUpdate();
                    }
                }
                insertAudit(
                        connection,
                        "PUNISHMENT_CODE_ROTATED",
                        actorId,
                        sanction.targetId(),
                        sanction.caseId(),
                        Map.of("punishmentId", punishmentId.toString(), "generation", generation),
                        now
                );
                connection.commit();
                return new PunishmentCodeDisplay(
                        punishmentId,
                        sanction.caseId(),
                        generation,
                        WebsitePunishmentProjection.publicType(sanction.sanctionType()),
                        derived
                );
            } catch (SQLException | JsonProcessingException | ArithmeticException exception) {
                rollback(connection, exception);
                throw persistence("Unable to rotate the punishment code", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open a punishment-code transaction", exception);
        }
    }

    @Override
    public boolean revokeCode(UUID punishmentId, UUID actorId, Instant now) {
        if (punishmentId == null || actorId == null || now == null) {
            throw invalid("INVALID_CODE_REVOCATION", "The code revocation request is invalid");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SanctionRow sanction = selectSanction(connection, punishmentId, true);
                if (sanction == null) {
                    connection.rollback();
                    throw notFound("PUNISHMENT_NOT_FOUND", "The punishment could not be found");
                }
                int changed;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE punishment_codes
                        SET status = 'REVOKED', revoked_at = ?, revoked_by = ?
                        WHERE sanction_id = ? AND status = 'ACTIVE'
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setBytes(2, UuidBytes.toBytes(actorId));
                    statement.setBytes(3, UuidBytes.toBytes(punishmentId));
                    changed = statement.executeUpdate();
                }
                if (changed == 1) {
                    insertAudit(
                            connection,
                            "PUNISHMENT_CODE_REVOKED",
                            actorId,
                            sanction.targetId(),
                            sanction.caseId(),
                            Map.of("punishmentId", punishmentId.toString()),
                            now
                    );
                }
                connection.commit();
                return changed == 1;
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw persistence("Unable to revoke the punishment code", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open a punishment-code transaction", exception);
        }
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
                    if ("REJECTED".equals(existing.state())) {
                        return new AppealAcceptancePreparation.Rejected(
                                existing.outcomeCode() == null ? "APPEAL_REJECTED" : existing.outcomeCode(),
                                "The appeal acceptance was previously rejected"
                        );
                    }
                    return new AppealAcceptancePreparation.Ready(true);
                }
                CodeRow row = selectCodeBySanction(connection, punishmentId, true);
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
        if (appealId == null || now == null || !List.of("APPLIED", "REJECTED").contains(state)
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
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Appeal state changed during completion");
                    }
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

    private PublicRow readPublicRow(ResultSet result, Instant now) throws SQLException {
        UUID sanctionId = UuidBytes.fromBytes(result.getBytes("sanction_id"));
        Instant issuedAt = result.getTimestamp("issued_at").toInstant();
        Timestamp expirationValue = result.getTimestamp("expiration_at");
        Instant expiration = expirationValue == null ? null : expirationValue.toInstant();
        PublicPunishmentState state = WebsitePunishmentProjection.publicState(
                result.getString("status"), expiration, now
        );
        OptionalLong remaining = expiration == null
                ? OptionalLong.empty()
                : OptionalLong.of(state == PublicPunishmentState.ACTIVE
                        ? Math.max(0, Duration.between(now, expiration).toSeconds())
                        : 0);
        String sanctionType = result.getString("sanction_type");
        boolean appealAvailable = state == PublicPunishmentState.ACTIVE
                && WebsitePunishmentProjection.isCodeEligibleType(sanctionType)
                && "ACTIVE".equals(result.getString("code_status"));
        PublicPunishment punishment = new PublicPunishment(
                result.getString("current_username"),
                WebsitePunishmentProjection.publicType(sanctionType),
                result.getString("sanction_family"),
                result.getString("public_reason"),
                issuedAt,
                Optional.ofNullable(expiration),
                remaining,
                state,
                new CaseId(result.getString("case_id")),
                appealAvailable
        );
        return new PublicRow(sanctionId, issuedAt, punishment);
    }

    private CodeRow selectCodeByHash(Connection connection, byte[] hash, boolean lock) throws SQLException {
        String sql = CODE_ROW_SELECT + " WHERE pc.key_version = ? AND pc.code_hash = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, codeProtector.keyVersion());
            statement.setBytes(2, hash);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCodeRow(result) : null;
            }
        }
    }

    private static CodeRow selectCodeBySanction(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = CODE_ROW_SELECT + " WHERE pc.sanction_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCodeRow(result) : null;
            }
        }
    }

    private static CodeRow readCodeRow(ResultSet result) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        return new CodeRow(
                UuidBytes.fromBytes(result.getBytes("sanction_id")),
                new CaseId(result.getString("case_id")),
                result.getInt("key_version"),
                result.getInt("generation"),
                result.getBytes("code_hash"),
                result.getString("code_status"),
                result.getBytes("claimed_account_token"),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                result.getString("sanction_type"),
                result.getString("sanction_status"),
                expiration == null ? null : expiration.toInstant(),
                result.getString("case_state"),
                result.getString("current_username")
        );
    }

    private static SanctionRow selectSanction(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT s.sanction_id, s.case_id, s.target_id, s.sanction_type,
                       s.status AS sanction_status, s.expiration_at, c.state AS case_state
                FROM sanctions s
                JOIN cases c ON c.case_id = s.case_id
                WHERE s.sanction_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readSanction(result) : null;
            }
        }
    }

    private static SanctionRow readSanction(ResultSet result) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        return new SanctionRow(
                UuidBytes.fromBytes(result.getBytes("sanction_id")),
                new CaseId(result.getString("case_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                result.getString("sanction_type"),
                result.getString("sanction_status"),
                expiration == null ? null : expiration.toInstant(),
                result.getString("case_state")
        );
    }

    private static CodeRecord selectCodeRecord(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = """
                SELECT key_version, generation, code_hash, status
                FROM punishment_codes
                WHERE sanction_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new CodeRecord(
                                result.getInt("key_version"),
                                result.getInt("generation"),
                                result.getBytes("code_hash"),
                                result.getString("status")
                        )
                        : null;
            }
        }
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
        insertCodeRow(connection, sanction, generation, hash, now, actorId);
        return new CodeRecord(codeProtector.keyVersion(), generation, hash, "ACTIVE");
    }

    private void insertCodeRow(
            Connection connection,
            SanctionRow sanction,
            int generation,
            byte[] hash,
            Instant now,
            UUID actorId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_codes(
                    sanction_id, case_id, key_version, generation, code_hash, status,
                    created_at, rotated_at, rotated_by
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sanction.sanctionId()));
            statement.setString(2, sanction.caseId().value());
            statement.setInt(3, codeProtector.keyVersion());
            statement.setInt(4, generation);
            statement.setBytes(5, hash);
            statement.setTimestamp(6, Timestamp.from(now));
            if (actorId == null) {
                statement.setNull(7, Types.TIMESTAMP);
                statement.setNull(8, Types.BINARY);
            } else {
                statement.setTimestamp(7, Timestamp.from(now));
                statement.setBytes(8, UuidBytes.toBytes(actorId));
            }
            statement.executeUpdate();
        }
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

    private static boolean codeExists(Connection connection, UUID punishmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM punishment_codes WHERE sanction_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean usernameMatches(Connection connection, UUID targetId, String username)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM players p
                WHERE p.player_id = ?
                  AND (
                      p.lowercase_username = ?
                      OR EXISTS (
                          SELECT 1 FROM player_names history
                          WHERE history.player_id = p.player_id
                            AND history.lowercase_username = ?
                      )
                  )
                """)) {
            String lower = username.toLowerCase(Locale.ROOT);
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setString(2, lower);
            statement.setString(3, lower);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
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
                "ELIGIBLE".equals(eligibility),
                eligibility
        );
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
        return "ELIGIBLE".equals(WebsitePunishmentProjection.eligibilityState(
                "ACTIVE",
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
                    ExistingAppeal current = new ExistingAppeal(
                            UuidBytes.fromBytes(result.getBytes("appeal_id")),
                            UuidBytes.fromBytes(result.getBytes("punishment_id")),
                            new CaseId(result.getString("case_id")),
                            result.getBytes("player_account_token"),
                            result.getString("idempotency_key"),
                            result.getString("state"),
                            result.getString("outcome_code")
                    );
                    if (found != null) {
                        throw new SQLException("Appeal ID and idempotency key identify different requests");
                    }
                    found = current;
                }
                return found;
            }
        }
    }

    private void insertAudit(
            Connection connection,
            String eventType,
            UUID actorId,
            UUID targetId,
            CaseId caseId,
            Map<String, Object> details,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            if (actorId == null) {
                statement.setNull(3, Types.BINARY);
            } else {
                statement.setBytes(3, UuidBytes.toBytes(actorId));
            }
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, caseId.value());
            statement.setString(6, eventType);
            statement.setString(7, json.writeValueAsString(details));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
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

    private static WebsiteModerationException ineligible(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INELIGIBLE, code, message);
    }

    private static WebsiteModerationException unavailable(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.UNAVAILABLE, code, message);
    }

    private static ModerationPersistenceException persistence(String message, Exception exception) {
        return exception instanceof ModerationPersistenceException persistenceException
                ? persistenceException
                : new ModerationPersistenceException(message, exception);
    }

    private record PublicRow(UUID sanctionId, Instant issuedAt, PublicPunishment punishment) {
    }

    private record CodeRow(
            UUID sanctionId,
            CaseId caseId,
            int keyVersion,
            int generation,
            byte[] codeHash,
            String codeStatus,
            byte[] claimedAccountToken,
            UUID targetId,
            String sanctionType,
            String sanctionStatus,
            Instant expiration,
            String caseState,
            String currentUsername
    ) {
    }

    private record SanctionRow(
            UUID sanctionId,
            CaseId caseId,
            UUID targetId,
            String sanctionType,
            String sanctionStatus,
            Instant expiration,
            String caseState
    ) {
    }

    private record CodeRecord(int keyVersion, int generation, byte[] codeHash, String status) {
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
