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

// SQL binding and transaction steps intentionally remain together so row locks, replay checks,
// state transitions, and event writes can be reviewed as one atomic workflow.
@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.CyclomaticComplexity",
        "PMD.ExcessiveClassLength",
        "PMD.NcssCount"
})
final class JdbcWebsiteAppealWorkflowStore {
    private static final String ALL = "ALL";
    private static final String OPEN = "OPEN";
    private static final String INFORMATION_REQUESTED = "INFORMATION_REQUESTED";
    private static final String APPROVAL_PENDING = "APPROVAL_PENDING";
    private static final String DENIED = "DENIED";
    private static final List<String> REVIEW_STATES = List.of(
            ALL,
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
    private final JdbcWebsiteAppealRateLimiter rateLimiter;

    JdbcWebsiteAppealWorkflowStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            JdbcPunishmentCodeRepository punishmentCodes,
            JdbcWebsiteAppealRateLimiter rateLimiter
    ) {
        if (dataSource == null || codeProtector == null || punishmentCodes == null
                || rateLimiter == null) {
            throw new IllegalArgumentException("Website appeal workflow dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
        this.punishmentCodes = punishmentCodes;
        this.rateLimiter = rateLimiter;
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
        SubmissionRequest request = new SubmissionRequest(
                punishmentId, accountId, username, reason, idempotencyKey, now
        );
        validateSubmission(request);
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
                rateLimiter.enforce(connection, accountId, punishmentId, idempotencyKey, now);
                AppealRow existing = selectByPunishment(connection, punishmentId, true);
                WebsiteAppealSubmission result = writeSubmission(
                        connection, code, existing, accountToken, request
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

    private WebsiteAppealSubmission writeSubmission(
            Connection connection,
            JdbcPunishmentCodeRepository.CodeRow code,
            AppealRow existing,
            byte[] accountToken,
            SubmissionRequest request
    ) throws SQLException {
        return existing == null
                ? insertSubmission(connection, code, accountToken, request)
                : resubmit(connection, existing, accountToken, request);
    }

    WebsiteAppealPage list(
            String state,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        String normalizedState = normalizeState(state);
        Optional<Cursor> cursor = decodeCursor(encodedCursor);
        if (limit < 1 || limit > 100 || now == null) {
            throw invalid("INVALID_APPEAL_LIST", "The appeal list request is invalid");
        }
        AppealListQuery query = new AppealListQuery(normalizedState, cursor, limit);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(appealListSql(query))) {
            bindAppealList(statement, query);
            try (ResultSet result = statement.executeQuery()) {
                return readAppealPage(result, limit);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to list website appeals", exception);
        }
    }

    private static String appealListSql(AppealListQuery query) {
        String stateClause = ALL.equals(query.state()) ? "" : " AND a.state = ?";
        String cursorClause = query.cursor().isEmpty() ? "" : """
                 AND (a.updated_at < ? OR (a.updated_at = ? AND a.appeal_id < ?))
                """;
        return APPEAL_SELECT + """
                WHERE a.player_username IS NOT NULL
                """ + stateClause + cursorClause + """
                ORDER BY a.updated_at DESC, a.appeal_id DESC
                LIMIT ?
                """;
    }

    private static void bindAppealList(PreparedStatement statement, AppealListQuery query) throws SQLException {
        int index = 1;
        if (!ALL.equals(query.state())) {
            statement.setString(index++, query.state());
        }
        if (query.cursor().isPresent()) {
            Cursor cursor = query.cursor().orElseThrow();
            Timestamp timestamp = Timestamp.from(cursor.updatedAt());
            statement.setTimestamp(index++, timestamp);
            statement.setTimestamp(index++, timestamp);
            statement.setBytes(index++, UuidBytes.toBytes(cursor.appealId()));
        }
        statement.setInt(index, query.limit() + 1);
    }

    private static WebsiteAppealPage readAppealPage(ResultSet result, int limit) throws SQLException {
        List<WebsiteAppealView> items = new ArrayList<>();
        while (result.next()) {
            items.add(view(readAppeal(result)));
        }
        if (items.size() <= limit) {
            return new WebsiteAppealPage(items, Optional.empty());
        }
        items.removeLast();
        WebsiteAppealView last = items.getLast();
        return new WebsiteAppealPage(
                items,
                Optional.of(encodeCursor(last.updatedAt(), last.appealId()))
        );
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
        DecisionRequest request = DecisionRequest.normalized(
                appealId,
                expectedVersion,
                decision,
                note,
                reviewerAccountId,
                reviewerRank,
                idempotencyKey,
                now
        );
        validateDecision(request);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DecisionOutcome outcome = prepareDecision(connection, request);
                if (outcome.replayed()) {
                    connection.rollback();
                } else {
                    connection.commit();
                }
                return outcome.preparation();
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

    private DecisionOutcome prepareDecision(Connection connection, DecisionRequest request) throws SQLException {
        AppealRow existing = selectById(connection, request.appealId(), true);
        requireAppealExists(existing);
        Optional<WebsiteAppealDecisionPreparation> replay = replayDecision(existing, request);
        if (replay.isPresent()) {
            return new DecisionOutcome(replay.orElseThrow(), true);
        }
        requireReviewable(existing, request);
        DecisionTransition transition = decisionTransition(existing, request.decision());
        updateDecision(connection, existing, transition, request);
        insertEvent(connection, decisionEvent(request, transition));
        AppealRow updated = selectById(connection, request.appealId(), false);
        return new DecisionOutcome(
                preparation(updated, false, transition.requiresAcceptance()),
                false
        );
    }

    private static void requireAppealExists(AppealRow existing) {
        if (existing == null || existing.playerUsername() == null) {
            throw notFound("APPEAL_NOT_FOUND", "The appeal could not be found");
        }
    }

    private static void requireReviewable(AppealRow existing, DecisionRequest request) {
        if (existing.revision() != request.expectedVersion()) {
            throw conflict("STALE_APPEAL_STATE", "The appeal changed; reload it and retry");
        }
        if (!OPEN.equals(existing.state()) && !INFORMATION_REQUESTED.equals(existing.state())) {
            throw conflict("APPEAL_STATE_CONFLICT", "The appeal is no longer reviewable");
        }
    }

    private static DecisionTransition decisionTransition(AppealRow existing, String decision) {
        return switch (decision) {
            case "APPROVE" -> new DecisionTransition(
                    APPROVAL_PENDING, existing.revision() + 1, "APPROVAL_REQUESTED", true
            );
            case "DENY" -> new DecisionTransition(DENIED, existing.revision() + 1, DENIED, false);
            case "REQUEST_INFORMATION" -> new DecisionTransition(
                    INFORMATION_REQUESTED, existing.revision() + 1, INFORMATION_REQUESTED, false
            );
            default -> throw invalid("INVALID_APPEAL_DECISION", "The decision is invalid");
        };
    }

    private static AppealEvent decisionEvent(DecisionRequest request, DecisionTransition transition) {
        return new AppealEvent(
                request.appealId(),
                transition.revision(),
                transition.eventType(),
                request.reviewerAccountId(),
                request.reviewerRank(),
                request.note(),
                request.idempotencyKey(),
                request.now()
        );
    }

    private static WebsiteAppealDecisionPreparation preparation(
            AppealRow row,
            boolean replayed,
            boolean requiresAcceptance
    ) {
        return new WebsiteAppealDecisionPreparation(
                view(row),
                replayed,
                requiresAcceptance,
                acceptanceAccountId(row, requiresAcceptance)
        );
    }

    private static String acceptanceAccountId(AppealRow row, boolean requiresAcceptance) {
        if (requiresAcceptance) {
            return row.playerAccountId();
        }
        return null;
    }

    private WebsiteAppealSubmission insertSubmission(
            Connection connection,
            JdbcPunishmentCodeRepository.CodeRow code,
            byte[] accountToken,
            SubmissionRequest request
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
            statement.setString(5, request.accountId());
            statement.setString(6, request.username());
            statement.setString(7, request.reason());
            statement.setString(8, "workflow:" + appealId);
            statement.setString(9, request.idempotencyKey());
            statement.setTimestamp(10, Timestamp.from(request.now()));
            statement.setTimestamp(11, Timestamp.from(request.now()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal was not inserted"
            );
        }
        insertEvent(connection, new AppealEvent(
                appealId, 1, "SUBMITTED", null, null,
                request.reason(), request.idempotencyKey(), request.now()
        ));
        return new WebsiteAppealSubmission(view(selectById(connection, appealId, false)), false);
    }

    private WebsiteAppealSubmission resubmit(
            Connection connection,
            AppealRow existing,
            byte[] accountToken,
            SubmissionRequest request
    ) throws SQLException {
        if (existing.submissionIdempotencyKey() != null
                && existing.submissionIdempotencyKey().equals(request.idempotencyKey())) {
            requireSameSubmission(existing, accountToken, request);
            return new WebsiteAppealSubmission(view(existing), true);
        }
        if (!INFORMATION_REQUESTED.equals(existing.state())) {
            throw conflict("APPEAL_ALREADY_EXISTS", "That punishment already has an appeal");
        }
        requireSameAccount(existing, accountToken, request);
        long nextRevision = existing.revision() + 1;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET appeal_reason = ?, submission_idempotency_key = ?, state = 'OPEN',
                    revision = ?, decision_type = NULL, decision_idempotency_key = NULL,
                    reviewer_account_id = NULL, reviewer_rank = NULL,
                    decision_note = NULL, decided_at = NULL, updated_at = ?
                WHERE appeal_id = ? AND revision = ? AND state = 'INFORMATION_REQUESTED'
                """)) {
            statement.setString(1, request.reason());
            statement.setString(2, request.idempotencyKey());
            statement.setLong(3, nextRevision);
            statement.setTimestamp(4, Timestamp.from(request.now()));
            statement.setBytes(5, UuidBytes.toBytes(existing.appealId()));
            statement.setLong(6, existing.revision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal changed during resubmission"
            );
        }
        insertEvent(connection, new AppealEvent(
                existing.appealId(), nextRevision, "RESUBMITTED", null, null,
                request.reason(), request.idempotencyKey(), request.now()
        ));
        return new WebsiteAppealSubmission(
                view(selectById(connection, existing.appealId(), false)),
                false
        );
    }

    private void updateDecision(
            Connection connection,
            AppealRow existing,
            DecisionTransition transition,
            DecisionRequest request
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET state = ?, revision = ?, decision_type = ?,
                    decision_idempotency_key = ?, reviewer_account_id = ?,
                    reviewer_rank = ?, decision_note = ?, decided_at = ?, updated_at = ?
                WHERE appeal_id = ? AND revision = ?
                """)) {
            statement.setString(1, transition.state());
            statement.setLong(2, transition.revision());
            statement.setString(3, request.decision());
            statement.setString(4, request.idempotencyKey());
            statement.setBytes(5, UuidBytes.toBytes(request.reviewerAccountId()));
            statement.setString(6, request.reviewerRank());
            statement.setString(7, request.note());
            statement.setTimestamp(8, Timestamp.from(request.now()));
            statement.setTimestamp(9, Timestamp.from(request.now()));
            statement.setBytes(10, UuidBytes.toBytes(existing.appealId()));
            statement.setLong(11, existing.revision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal changed during decision"
            );
        }
    }

    private static Optional<WebsiteAppealDecisionPreparation> replayDecision(
            AppealRow existing,
            DecisionRequest request
    ) {
        if (!request.idempotencyKey().equals(existing.decisionIdempotencyKey())) {
            return Optional.empty();
        }
        if (!request.decision().equals(existing.decisionType())
                || !request.note().equals(existing.decisionNote())
                || !request.reviewerAccountId().equals(existing.reviewerAccountId())
                || !request.reviewerRank().equals(existing.reviewerRank())) {
            throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The decision key conflicts with prior state");
        }
        boolean requiresAcceptance = "APPROVE".equals(request.decision())
                && APPROVAL_PENDING.equals(existing.state());
        return Optional.of(preparation(existing, true, requiresAcceptance));
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

    private static void insertEvent(Connection connection, AppealEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO website_appeal_events(
                    appeal_id, revision, event_type, actor_account_id,
                    actor_rank, note, idempotency_key, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(event.appealId()));
            statement.setLong(2, event.revision());
            statement.setString(3, event.type());
            if (event.actorAccountId() == null) {
                statement.setNull(4, Types.BINARY);
            } else {
                statement.setBytes(4, UuidBytes.toBytes(event.actorAccountId()));
            }
            if (event.actorRank() == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, event.actorRank());
            }
            statement.setString(6, event.note());
            statement.setString(7, event.idempotencyKey());
            statement.setTimestamp(8, Timestamp.from(event.now()));
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
        if (!appealable(code, now)) {
            throw conflict("PUNISHMENT_INELIGIBLE", "That punishment is no longer appealable");
        }
    }

    private static boolean appealable(JdbcPunishmentCodeRepository.CodeRow code, Instant now) {
        return "ACTIVE".equals(code.codeStatus())
                && List.of("ACTIVE", "APPLIED").contains(code.sanctionStatus())
                && (code.expiration() == null || code.expiration().isAfter(now))
                && !"FULLY_OVERTURNED".equals(code.caseState())
                && List.of("BAN", "NETWORK_BAN", "NETWORK_IDENTITY_BAN", "MUTE")
                        .contains(code.sanctionType());
    }

    private static void requireSameSubmission(
            AppealRow existing,
            byte[] accountToken,
            SubmissionRequest request
    ) {
        requireSameAccount(existing, accountToken, request);
        if (!request.reason().equals(existing.reason())) {
            throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The appeal key conflicts with prior state");
        }
    }

    private static void requireSameAccount(
            AppealRow existing,
            byte[] accountToken,
            SubmissionRequest request
    ) {
        if (!request.accountId().equals(existing.playerAccountId())
                || !MessageDigest.isEqual(accountToken, existing.accountToken())
                || !request.username().equals(existing.playerUsername())) {
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

    private static Optional<Cursor> decodeCursor(Optional<String> encoded) {
        if (encoded.isEmpty() || encoded.orElseThrow().isBlank()) {
            return Optional.empty();
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encoded.orElseThrow()),
                    java.nio.charset.StandardCharsets.US_ASCII
            );
            int separator = decoded.indexOf(':');
            return Optional.of(new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(decoded.substring(0, separator))),
                    UUID.fromString(decoded.substring(separator + 1))
            ));
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

    private static void validateAccountRequest(String accountId, int limit, Instant now) {
        if (accountId == null || accountId.isBlank() || accountId.length() > 128
                || limit < 1 || limit > 100 || now == null) {
            throw invalid("INVALID_APPEAL_ELIGIBILITY", "The eligibility request is invalid");
        }
    }

    private static void validateSubmission(SubmissionRequest request) {
        requireValidSubmission(request.punishmentId() != null);
        requireValidSubmission(validNonBlankLength(request.accountId(), 1, 128));
        requireValidSubmission(request.username() != null
                && request.username().matches("[A-Za-z0-9_]{1,16}"));
        requireValidSubmission(validLength(request.reason(), 10, 1_000));
        requireValidSubmission(validIdempotencyKey(request.idempotencyKey()));
        requireValidSubmission(request.now() != null);
    }

    private static void validateDecision(DecisionRequest request) {
        requireValidDecision(request.appealId() != null && request.expectedVersion() >= 1);
        requireValidDecision(List.of("APPROVE", "DENY", "REQUEST_INFORMATION").contains(request.decision()));
        requireValidDecision(validLength(request.note(), 3, 1_000));
        requireValidDecision(request.reviewerAccountId() != null);
        requireValidDecision(List.of("MOD", "ADMIN", "FOUNDER").contains(request.reviewerRank()));
        requireValidDecision(validIdempotencyKey(request.idempotencyKey()));
        requireValidDecision(request.now() != null);
    }

    private static boolean validLength(String value, int minimum, int maximum) {
        return value != null && value.length() >= minimum && value.length() <= maximum;
    }

    private static boolean validNonBlankLength(String value, int minimum, int maximum) {
        return validLength(value, minimum, maximum) && !value.isBlank();
    }

    private static void requireValidSubmission(boolean valid) {
        if (!valid) {
            throw invalid("INVALID_APPEAL", "The appeal submission is invalid");
        }
    }

    private static void requireValidDecision(boolean valid) {
        if (!valid) {
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

    private record AppealListQuery(String state, Optional<Cursor> cursor, int limit) {
    }

    private record SubmissionRequest(
            UUID punishmentId,
            String accountId,
            String username,
            String reason,
            String idempotencyKey,
            Instant now
    ) {
    }

    private record DecisionRequest(
            UUID appealId,
            long expectedVersion,
            String decision,
            String note,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) {
        private static DecisionRequest normalized(
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
            return new DecisionRequest(
                    appealId,
                    expectedVersion,
                    normalizedDecision,
                    note,
                    reviewerAccountId,
                    reviewerRank,
                    idempotencyKey,
                    now
            );
        }
    }

    private record DecisionTransition(
            String state,
            long revision,
            String eventType,
            boolean requiresAcceptance
    ) {
    }

    private record DecisionOutcome(WebsiteAppealDecisionPreparation preparation, boolean replayed) {
    }

    private record AppealEvent(
            UUID appealId,
            long revision,
            String type,
            UUID actorAccountId,
            String actorRank,
            String note,
            String idempotencyKey,
            Instant now
    ) {
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
