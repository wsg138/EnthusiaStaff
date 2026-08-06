package net.enthusia.staff.persistence;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.website.WebsiteAppealCandidate;
import net.enthusia.staff.domain.website.WebsiteAppealDecisionPreparation;
import net.enthusia.staff.domain.website.WebsiteAppealPage;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;
import net.enthusia.staff.domain.website.WebsiteAppealView;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class JdbcWebsiteAppealWorkflowStore {
    private static final String OPEN = "OPEN";
    private static final String INFORMATION_REQUESTED = "INFORMATION_REQUESTED";
    private static final String APPROVAL_PENDING = "APPROVAL_PENDING";
    private static final String DENIED = "DENIED";
    private static final List<String> REVIEW_STATES = List.of(
            "ALL",
            OPEN,
            INFORMATION_REQUESTED,
            APPROVAL_PENDING,
            "APPLIED",
            DENIED,
            "REJECTED"
    );
    private static final String APPEAL_SELECT = """
            SELECT a.appeal_id, a.punishment_id, a.case_id, a.player_account_token,
                   a.player_account_id, a.player_username, a.appeal_reason, a.state,
                   a.revision, a.decision_type, a.decision_idempotency_key,
                   a.reviewer_account_id, a.reviewer_rank, a.decision_note,
                   a.submission_idempotency_key, a.created_at, a.updated_at,
                   a.outcome_code, s.sanction_type
            FROM website_appeal_requests a
            JOIN sanctions s ON s.sanction_id = a.punishment_id
            """;

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;
    private final JdbcPunishmentCodeRepository punishmentCodes;

    JdbcWebsiteAppealWorkflowStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            JdbcPunishmentCodeRepository punishmentCodes
    ) {
        if (dataSource == null || codeProtector == null || punishmentCodes == null) {
            throw new IllegalArgumentException("Website appeal workflow dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
        this.punishmentCodes = punishmentCodes;
    }

    List<WebsiteAppealCandidate> eligible(String accountId, int limit, Instant now) {
        validateAccountRequest(accountId, limit, now);
        byte[] accountToken = accountToken(accountId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT pc.sanction_id, pc.case_id, s.sanction_type,
                            COALESCE(NULLIF(c.public_reason, ''), 'No public reason provided')
                                AS public_reason,
                            s.issued_at
                     FROM punishment_codes pc
                     JOIN sanctions s ON s.sanction_id = pc.sanction_id
                     JOIN cases c ON c.case_id = pc.case_id
                     LEFT JOIN website_appeal_requests a
                         ON a.punishment_id = pc.sanction_id
                     WHERE pc.claimed_account_token = ?
                       AND pc.status = 'ACTIVE'
                       AND s.status IN ('ACTIVE', 'APPLIED')
                       AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                       AND s.sanction_type IN (
                           'BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN', 'MUTE'
                       )
                       AND c.state <> 'FULLY_OVERTURNED'
                       AND (a.appeal_id IS NULL OR a.state = 'INFORMATION_REQUESTED')
                     ORDER BY s.issued_at DESC, pc.sanction_id DESC
                     LIMIT ?
                     """)) {
            statement.setBytes(1, accountToken);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<WebsiteAppealCandidate> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(new WebsiteAppealCandidate(
                            UuidBytes.fromBytes(result.getBytes("sanction_id")),
                            new CaseId(result.getString("case_id")),
                            result.getString("sanction_type"),
                            result.getString("public_reason"),
                            result.getTimestamp("issued_at").toInstant()
                    ));
                }
                return List.copyOf(candidates);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to list appealable punishments", exception);
        }
    }

    WebsiteAppealSubmission submit(
            UUID punishmentId,
            String accountId,
            String username,
            String reason,
            String idempotencyKey,
            Instant now
    ) {
        validateSubmission(punishmentId, accountId, username, reason, idempotencyKey, now);
        byte[] accountToken = accountToken(accountId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                JdbcPunishmentCodeRepository.CodeRow code = punishmentCodes.selectCodeBySanction(
                        connection,
                        punishmentId,
                        true
                );
                requireEligibleBinding(code, accountToken, now);
                AppealRow existing = selectByPunishment(connection, punishmentId, true);
                WebsiteAppealSubmission result = existing == null
                        ? insertSubmission(
                                connection,
                                code,
                                accountId,
                                accountToken,
                                username,
                                reason,
                                idempotencyKey,
                                now
                        )
                        : resubmit(
                                connection,
                                existing,
                                accountId,
                                accountToken,
                                username,
                                reason,
                                idempotencyKey,
                                now
                        );
                connection.commit();
                return result;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to submit the website appeal", exception);
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open the website appeal transaction", exception);
        }
    }

    WebsiteAppealPage list(
            String state,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        String normalizedState = normalizeState(state);
        Cursor cursor = decodeCursor(encodedCursor);
        if (limit < 1 || limit > 100 || now == null) {
            throw invalid("INVALID_APPEAL_LIST", "The appeal list request is invalid");
        }
        String stateClause = "ALL".equals(normalizedState) ? "" : " AND a.state = ?";
        String cursorClause = cursor == null ? "" : """
                 AND (a.updated_at < ? OR (a.updated_at = ? AND a.appeal_id < ?))
                """;
        String sql = APPEAL_SELECT + """
                WHERE a.player_username IS NOT NULL
                """ + stateClause + cursorClause + """
                ORDER BY a.updated_at DESC, a.appeal_id DESC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (!"ALL".equals(normalizedState)) {
                statement.setString(index++, normalizedState);
            }
            if (cursor != null) {
                Timestamp timestamp = Timestamp.from(cursor.updatedAt());
                statement.setTimestamp(index++, timestamp);
                statement.setTimestamp(index++, timestamp);
                statement.setBytes(index++, UuidBytes.toBytes(cursor.appealId()));
            }
            statement.setInt(index, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<WebsiteAppealView> items = new ArrayList<>();
                while (result.next()) {
                    items.add(view(readAppeal(result)));
                }
                Optional<String> nextCursor = Optional.empty();
                if (items.size() > limit) {
                    WebsiteAppealView overflow = items.removeLast();
                    WebsiteAppealView last = items.getLast();
                    nextCursor = Optional.of(encodeCursor(last.updatedAt(), last.appealId()));
                    if (overflow == null) {
                        throw new IllegalStateException("Appeal pagination overflow is unavailable");
                    }
                }
                return new WebsiteAppealPage(items, nextCursor);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to list website appeals", exception);
        }
    }

    WebsiteAppealDecisionPreparation prepareDecision(
            UUID appealId,
            long expectedVersion,
            String decision,
            String note,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) {
        validateDecision(
                appealId,
                expectedVersion,
                decision,
                note,
                reviewerAccountId,
                reviewerRank,
                idempotencyKey,
                now
        );
        String normalizedDecision = decision.toUpperCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AppealRow existing = selectById(connection, appealId, true);
                if (existing == null || existing.playerUsername() == null) {
                    throw notFound("APPEAL_NOT_FOUND", "The appeal could not be found");
                }
                WebsiteAppealDecisionPreparation replay = replayDecision(
                        existing,
                        normalizedDecision,
                        note,
                        reviewerAccountId,
                        reviewerRank,
                        idempotencyKey
                );
                if (replay != null) {
                    connection.rollback();
                    return replay;
                }
                if (existing.revision() != expectedVersion) {
                    throw conflict("STALE_APPEAL_STATE", "The appeal changed; reload it and retry");
                }
                if (!OPEN.equals(existing.state())
                        && !INFORMATION_REQUESTED.equals(existing.state())) {
                    throw conflict("APPEAL_STATE_CONFLICT", "The appeal is no longer reviewable");
                }
                String nextState = switch (normalizedDecision) {
                    case "APPROVE" -> APPROVAL_PENDING;
                    case "DENY" -> DENIED;
                    case "REQUEST_INFORMATION" -> INFORMATION_REQUESTED;
                    default -> throw invalid("INVALID_APPEAL_DECISION", "The decision is invalid");
                };
                long nextRevision = existing.revision() + 1;
                updateDecision(
                        connection,
                        existing,
                        nextState,
                        nextRevision,
                        normalizedDecision,
                        note,
                        reviewerAccountId,
                        reviewerRank,
                        idempotencyKey,
                        now
                );
                insertEvent(
                        connection,
                        appealId,
                        nextRevision,
                        eventType(normalizedDecision),
                        reviewerAccountId,
                        reviewerRank,
                        note,
                        idempotencyKey,
                        now
                );
                AppealRow updated = selectById(connection, appealId, false);
                connection.commit();
                boolean requiresAcceptance = "APPROVE".equals(normalizedDecision);
                return new WebsiteAppealDecisionPreparation(
                        view(updated),
                        false,
                        requiresAcceptance,
                        requiresAcceptance ? updated.playerAccountId() : null
                );
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence("Unable to prepare the appeal decision", exception);
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to open the appeal decision transaction", exception);
        }
    }

    private WebsiteAppealSubmission insertSubmission(
            Connection connection,
            JdbcPunishmentCodeRepository.CodeRow code,
            String accountId,
            byte[] accountToken,
            String username,
            String reason,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        UUID appealId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO website_appeal_requests(
                    appeal_id, punishment_id, case_id, player_account_token,
                    player_account_id, player_username, appeal_reason, idempotency_key,
                    submission_idempotency_key, state, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 1, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            statement.setBytes(2, UuidBytes.toBytes(code.sanctionId()));
            statement.setString(3, code.caseId().value());
            statement.setBytes(4, accountToken);
            statement.setString(5, accountId);
            statement.setString(6, username);
            statement.setString(7, reason);
            statement.setString(8, "workflow:" + appealId);
            statement.setString(9, idempotencyKey);
            statement.setTimestamp(10, Timestamp.from(now));
            statement.setTimestamp(11, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal was not inserted"
            );
        }
        insertEvent(
                connection,
                appealId,
                1,
                "SUBMITTED",
                null,
                null,
                reason,
                idempotencyKey,
                now
        );
        return new WebsiteAppealSubmission(view(selectById(connection, appealId, false)), false);
    }

    private WebsiteAppealSubmission resubmit(
            Connection connection,
            AppealRow existing,
            String accountId,
            byte[] accountToken,
            String username,
            String reason,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        if (existing.submissionIdempotencyKey() != null
                && existing.submissionIdempotencyKey().equals(idempotencyKey)) {
            requireSameSubmission(existing, accountId, accountToken, username, reason);
            return new WebsiteAppealSubmission(view(existing), true);
        }
        if (!INFORMATION_REQUESTED.equals(existing.state())) {
            throw conflict("APPEAL_ALREADY_EXISTS", "That punishment already has an appeal");
        }
        requireSameAccount(existing, accountId, accountToken, username);
        long nextRevision = existing.revision() + 1;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET appeal_reason = ?, submission_idempotency_key = ?, state = 'OPEN',
                    revision = ?, decision_type = NULL, decision_idempotency_key = NULL,
                    reviewer_account_id = NULL, reviewer_rank = NULL,
                    decision_note = NULL, decided_at = NULL, updated_at = ?
                WHERE appeal_id = ? AND revision = ? AND state = 'INFORMATION_REQUESTED'
                """)) {
            statement.setString(1, reason);
            statement.setString(2, idempotencyKey);
            statement.setLong(3, nextRevision);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setBytes(5, UuidBytes.toBytes(existing.appealId()));
            statement.setLong(6, existing.revision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal changed during resubmission"
            );
        }
        insertEvent(
                connection,
                existing.appealId(),
                nextRevision,
                "RESUBMITTED",
                null,
                null,
                reason,
                idempotencyKey,
                now
        );
        return new WebsiteAppealSubmission(
                view(selectById(connection, existing.appealId(), false)),
                false
        );
    }

    private void updateDecision(
            Connection connection,
            AppealRow existing,
            String nextState,
            long nextRevision,
            String decision,
            String note,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET state = ?, revision = ?, decision_type = ?,
                    decision_idempotency_key = ?, reviewer_account_id = ?,
                    reviewer_rank = ?, decision_note = ?, decided_at = ?, updated_at = ?,
                    idempotency_key = CASE WHEN ? = 'APPROVE' THEN ? ELSE idempotency_key END
                WHERE appeal_id = ? AND revision = ?
                """)) {
            statement.setString(1, nextState);
            statement.setLong(2, nextRevision);
            statement.setString(3, decision);
            statement.setString(4, idempotencyKey);
            statement.setBytes(5, UuidBytes.toBytes(reviewerAccountId));
            statement.setString(6, reviewerRank);
            statement.setString(7, note);
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
            statement.setString(10, decision);
            statement.setString(11, idempotencyKey);
            statement.setBytes(12, UuidBytes.toBytes(existing.appealId()));
            statement.setLong(13, existing.revision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal changed during decision"
            );
        }
    }

    private static WebsiteAppealDecisionPreparation replayDecision(
            AppealRow existing,
            String decision,
            String note,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey
    ) {
        if (!idempotencyKey.equals(existing.decisionIdempotencyKey())) {
            return null;
        }
        if (!decision.equals(existing.decisionType()) || !note.equals(existing.decisionNote())
                || !reviewerAccountId.equals(existing.reviewerAccountId())
                || !reviewerRank.equals(existing.reviewerRank())) {
            throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The decision key conflicts with prior state");
        }
        boolean requiresAcceptance = "APPROVE".equals(decision)
                && APPROVAL_PENDING.equals(existing.state());
        return new WebsiteAppealDecisionPreparation(
                view(existing),
                true,
                requiresAcceptance,
                requiresAcceptance ? existing.playerAccountId() : null
        );
    }

    private AppealRow selectByPunishment(Connection connection, UUID punishmentId, boolean lock)
            throws SQLException {
        String sql = APPEAL_SELECT + " WHERE a.punishment_id = ?" + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readAppeal(result) : null;
            }
        }
    }

    private AppealRow selectById(Connection connection, UUID appealId, boolean lock)
            throws SQLException {
        String sql = APPEAL_SELECT + " WHERE a.appeal_id = ?" + lockClause(lock);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readAppeal(result) : null;
            }
        }
    }

    private static AppealRow readAppeal(ResultSet result) throws SQLException {
        Timestamp createdAt = result.getTimestamp("created_at");
        Timestamp updatedAt = result.getTimestamp("updated_at");
        byte[] reviewerBytes = result.getBytes("reviewer_account_id");
        return new AppealRow(
                UuidBytes.fromBytes(result.getBytes("appeal_id")),
                UuidBytes.fromBytes(result.getBytes("punishment_id")),
                new CaseId(result.getString("case_id")),
                result.getBytes("player_account_token"),
                result.getString("player_account_id"),
                result.getString("player_username"),
                result.getString("appeal_reason"),
                result.getString("state"),
                result.getLong("revision"),
                result.getString("decision_type"),
                result.getString("decision_idempotency_key"),
                reviewerBytes == null ? null : UuidBytes.fromBytes(reviewerBytes),
                result.getString("reviewer_rank"),
                result.getString("decision_note"),
                result.getString("submission_idempotency_key"),
                createdAt.toInstant(),
                updatedAt.toInstant(),
                result.getString("outcome_code"),
                result.getString("sanction_type")
        );
    }

    private static WebsiteAppealView view(AppealRow row) {
        return new WebsiteAppealView(
                row.appealId(),
                row.punishmentId(),
                row.caseId(),
                row.punishmentType(),
                row.playerUsername(),
                row.reason(),
                row.state(),
                row.revision(),
                row.decisionType(),
                row.decisionNote(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private static void insertEvent(
            Connection connection,
            UUID appealId,
            long revision,
            String eventType,
            UUID actorAccountId,
            String actorRank,
            String note,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO website_appeal_events(
                    appeal_id, revision, event_type, actor_account_id,
                    actor_rank, note, idempotency_key, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            statement.setLong(2, revision);
            statement.setString(3, eventType);
            if (actorAccountId == null) {
                statement.setNull(4, Types.BINARY);
            } else {
                statement.setBytes(4, UuidBytes.toBytes(actorAccountId));
            }
            if (actorRank == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, actorRank);
            }
            statement.setString(6, note);
            statement.setString(7, idempotencyKey);
            statement.setTimestamp(8, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal event was not inserted"
            );
        }
    }

    private static void requireEligibleBinding(
            JdbcPunishmentCodeRepository.CodeRow code,
            byte[] accountToken,
            Instant now
    ) {
        if (code == null) {
            throw notFound("PUNISHMENT_NOT_FOUND", "The punishment could not be found");
        }
        if (code.claimedAccountToken() == null
                || !MessageDigest.isEqual(code.claimedAccountToken(), accountToken)) {
            throw conflict("BINDING_ACCOUNT_MISMATCH", "The punishment is not bound to this account");
        }
        boolean active = "ACTIVE".equals(code.codeStatus())
                && List.of("ACTIVE", "APPLIED").contains(code.sanctionStatus())
                && (code.expiration() == null || code.expiration().isAfter(now))
                && !"FULLY_OVERTURNED".equals(code.caseState())
                && List.of("BAN", "NETWORK_BAN", "NETWORK_IDENTITY_BAN", "MUTE")
                        .contains(code.sanctionType());
        if (!active) {
            throw conflict("PUNISHMENT_INELIGIBLE", "That punishment is no longer appealable");
        }
    }

    private static void requireSameSubmission(
            AppealRow existing,
            String accountId,
            byte[] accountToken,
            String username,
            String reason
    ) {
        requireSameAccount(existing, accountId, accountToken, username);
        if (!reason.equals(existing.reason())) {
            throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The appeal key conflicts with prior state");
        }
    }

    private static void requireSameAccount(
            AppealRow existing,
            String accountId,
            byte[] accountToken,
            String username
    ) {
        if (!accountId.equals(existing.playerAccountId())
                || !MessageDigest.isEqual(accountToken, existing.accountToken())
                || !username.equals(existing.playerUsername())) {
            throw conflict("APPEAL_ACCOUNT_CONFLICT", "The appeal belongs to another account");
        }
    }

    private byte[] accountToken(String accountId) {
        try {
            return codeProtector.accountToken(accountId);
        } catch (IllegalArgumentException exception) {
            throw invalid("INVALID_ACCOUNT_ID", "The website account ID is invalid");
        }
    }

    private static String normalizeState(String state) {
        String normalized = state == null || state.isBlank()
                ? OPEN
                : state.toUpperCase(Locale.ROOT);
        if (!REVIEW_STATES.contains(normalized)) {
            throw invalid("INVALID_APPEAL_STATE", "The appeal state filter is invalid");
        }
        return normalized;
    }

    private static Cursor decodeCursor(Optional<String> encoded) {
        if (encoded.isEmpty() || encoded.orElseThrow().isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encoded.orElseThrow()),
                    java.nio.charset.StandardCharsets.US_ASCII
            );
            int separator = decoded.indexOf(':');
            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(decoded.substring(0, separator))),
                    UUID.fromString(decoded.substring(separator + 1))
            );
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw invalid("INVALID_APPEAL_CURSOR", "The appeal cursor is invalid");
        }
    }

    private static String encodeCursor(Instant updatedAt, UUID appealId) {
        String value = updatedAt.toEpochMilli() + ":" + appealId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }

    private static String eventType(String decision) {
        return switch (decision) {
            case "APPROVE" -> "APPROVAL_REQUESTED";
            case "DENY" -> "DENIED";
            case "REQUEST_INFORMATION" -> "INFORMATION_REQUESTED";
            default -> throw invalid("INVALID_APPEAL_DECISION", "The decision is invalid");
        };
    }

    private static void validateAccountRequest(String accountId, int limit, Instant now) {
        if (accountId == null || accountId.isBlank() || accountId.length() > 128
                || limit < 1 || limit > 100 || now == null) {
            throw invalid("INVALID_APPEAL_ELIGIBILITY", "The eligibility request is invalid");
        }
    }

    private static void validateSubmission(
            UUID punishmentId,
            String accountId,
            String username,
            String reason,
            String idempotencyKey,
            Instant now
    ) {
        if (punishmentId == null || accountId == null || accountId.isBlank()
                || accountId.length() > 128 || username == null
                || !username.matches("[A-Za-z0-9_]{1,16}") || reason == null
                || reason.length() < 10 || reason.length() > 1_000
                || !validIdempotencyKey(idempotencyKey) || now == null) {
            throw invalid("INVALID_APPEAL", "The appeal submission is invalid");
        }
    }

    private static void validateDecision(
            UUID appealId,
            long expectedVersion,
            String decision,
            String note,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) {
        String normalizedDecision = decision == null ? "" : decision.toUpperCase(Locale.ROOT);
        if (appealId == null || expectedVersion < 1
                || !List.of("APPROVE", "DENY", "REQUEST_INFORMATION")
                        .contains(normalizedDecision)
                || note == null || note.length() < 3 || note.length() > 1_000
                || reviewerAccountId == null || reviewerRank == null
                || !List.of("MOD", "ADMIN", "FOUNDER").contains(reviewerRank)
                || !validIdempotencyKey(idempotencyKey) || now == null) {
            throw invalid("INVALID_APPEAL_DECISION", "The appeal decision is invalid");
        }
    }

    private static boolean validIdempotencyKey(String value) {
        return value != null && value.length() >= 8 && value.length() <= 128
                && value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    private static String lockClause(boolean lock) {
        return lock ? " FOR UPDATE" : "";
    }

    private static void rollback(Connection connection, Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            exception.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Closing the connection is the final cleanup boundary.
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

    private record Cursor(Instant updatedAt, UUID appealId) {
    }

    private record AppealRow(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            byte[] accountToken,
            String playerAccountId,
            String playerUsername,
            String reason,
            String state,
            long revision,
            String decisionType,
            String decisionIdempotencyKey,
            UUID reviewerAccountId,
            String reviewerRank,
            String decisionNote,
            String submissionIdempotencyKey,
            Instant createdAt,
            Instant updatedAt,
            String outcomeCode,
            String punishmentType
    ) {
    }
}
