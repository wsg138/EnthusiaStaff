package net.enthusia.staff.persistence;

import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.conflict;
import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.invalid;
import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.notFound;
import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.persistence;
import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.validateEdit;
import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.validateReviewerMutation;
import static net.enthusia.staff.persistence.JdbcWebsiteAppealLifecycleSupport.validLength;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.website.WebsiteAppealMutation;
import net.enthusia.staff.domain.website.WebsiteAppealView;

final class JdbcWebsiteAppealLifecycleStore {
    private static final int EXPECTED_UPDATE_COUNT = 1;
    private static final String OPEN = "OPEN";
    private static final String EDIT = "EDIT";
    private static final String CLAIM = "CLAIM";
    private static final String REOPEN = "REOPEN";
    private static final String EDITED_EVENT = "WEBSITE_APPEAL_EDITED";
    private static final String CLAIMED_EVENT = "WEBSITE_APPEAL_CLAIMED";
    private static final String REOPENED_EVENT = "WEBSITE_APPEAL_REOPENED";
    private static final String SELECT_APPEAL = """
            SELECT a.appeal_id, a.punishment_id, a.case_id, a.player_account_token,
                   a.player_account_id, a.player_username, a.appeal_reason, a.state,
                   a.revision, a.decision_type, a.reviewer_account_id,
                   a.decision_note, a.created_at, a.updated_at, s.sanction_type
            FROM website_appeal_requests a
            JOIN sanctions s ON s.sanction_id = a.punishment_id
            WHERE a.appeal_id = ?
            """;

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;
    private final JdbcWebsiteAppealLifecycleAudit audit;

    JdbcWebsiteAppealLifecycleStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            ObjectMapper json
    ) {
        if (dataSource == null || codeProtector == null || json == null) {
            throw new IllegalArgumentException("Website appeal lifecycle dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
        this.audit = new JdbcWebsiteAppealLifecycleAudit(json);
    }

    WebsiteAppealMutation edit(
            UUID appealId,
            long expectedVersion,
            String accountId,
            String reason,
            String idempotencyKey,
            Instant now
    ) {
        validateEdit(appealId, expectedVersion, accountId, reason, idempotencyKey, now);
        byte[] accountToken = accountToken(accountId);
        return transaction("Unable to edit the website appeal", connection ->
                editLocked(connection, appealId, expectedVersion, accountId, accountToken,
                        reason, idempotencyKey, now));
    }

    WebsiteAppealMutation claim(
            UUID appealId,
            long expectedVersion,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) {
        validateReviewerMutation(
                appealId, expectedVersion, reviewerAccountId, reviewerRank, idempotencyKey, now, false
        );
        return transaction("Unable to claim the website appeal", connection ->
                claimLocked(connection, appealId, expectedVersion, reviewerAccountId,
                        reviewerRank, idempotencyKey, now));
    }

    WebsiteAppealMutation reopen(
            UUID appealId,
            long expectedVersion,
            UUID reviewerAccountId,
            String reviewerRank,
            String note,
            String idempotencyKey,
            Instant now
    ) {
        validateReviewerMutation(
                appealId, expectedVersion, reviewerAccountId, reviewerRank, idempotencyKey, now, true
        );
        if (!validLength(note, 3, 1_000)) {
            throw invalid("INVALID_REOPEN_NOTE", "The appeal reopen note is invalid");
        }
        return transaction("Unable to reopen the website appeal", connection ->
                reopenLocked(connection, appealId, expectedVersion, reviewerAccountId,
                        reviewerRank, note, idempotencyKey, now));
    }

    private WebsiteAppealMutation editLocked(
            Connection connection,
            UUID appealId,
            long expectedVersion,
            String accountId,
            byte[] accountToken,
            String reason,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        AppealRow current = requireAppeal(select(connection, appealId, true));
        Optional<WebsiteAppealMutation> replay = editReplay(
                connection, current, accountId, reason, idempotencyKey
        );
        if (replay.isPresent()) return replay.orElseThrow();
        requireOwner(current, accountId, accountToken);
        requireVersion(current, expectedVersion);
        requireEditable(current);
        long revision = current.revision() + 1;
        updateReason(connection, current, reason, revision, now);
        audit.write(connection, new JdbcWebsiteAppealLifecycleAudit.WriteRequest(
                appealId,
                EDIT,
                idempotencyKey,
                EDITED_EVENT,
                null,
                current.caseId(),
                editDetails(accountId, reason, revision),
                now
        ));
        return mutation(requireAppeal(select(connection, appealId, false)), false);
    }

    private WebsiteAppealMutation claimLocked(
            Connection connection,
            UUID appealId,
            long expectedVersion,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        AppealRow current = requireAppeal(select(connection, appealId, true));
        Optional<WebsiteAppealMutation> replay = reviewerReplay(
                connection, current, CLAIM, idempotencyKey, CLAIMED_EVENT,
                reviewerAccountId, reviewerRank, null
        );
        if (replay.isPresent()) return replay.orElseThrow();
        requireVersion(current, expectedVersion);
        if (OPEN.equals(current.state()) && reviewerAccountId.equals(current.reviewerAccountId())) {
            return mutation(current, true);
        }
        requireClaimable(current);
        long revision = current.revision() + 1;
        updateClaim(connection, current, reviewerAccountId, reviewerRank, revision, now);
        audit.write(connection, new JdbcWebsiteAppealLifecycleAudit.WriteRequest(
                appealId,
                CLAIM,
                idempotencyKey,
                CLAIMED_EVENT,
                reviewerAccountId,
                current.caseId(),
                reviewerDetails(reviewerRank, null, revision),
                now
        ));
        return mutation(requireAppeal(select(connection, appealId, false)), false);
    }

    private WebsiteAppealMutation reopenLocked(
            Connection connection,
            UUID appealId,
            long expectedVersion,
            UUID reviewerAccountId,
            String reviewerRank,
            String note,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        AppealRow current = requireAppeal(select(connection, appealId, true));
        Optional<WebsiteAppealMutation> replay = reviewerReplay(
                connection, current, REOPEN, idempotencyKey, REOPENED_EVENT,
                reviewerAccountId, reviewerRank, note
        );
        if (replay.isPresent()) return replay.orElseThrow();
        requireVersion(current, expectedVersion);
        requireReopenable(current);
        long revision = current.revision() + 1;
        updateReopen(connection, current, revision, now);
        audit.write(connection, new JdbcWebsiteAppealLifecycleAudit.WriteRequest(
                appealId,
                REOPEN,
                idempotencyKey,
                REOPENED_EVENT,
                reviewerAccountId,
                current.caseId(),
                reviewerDetails(reviewerRank, note, revision),
                now
        ));
        return mutation(requireAppeal(select(connection, appealId, false)), false);
    }

    private Optional<WebsiteAppealMutation> editReplay(
            Connection connection,
            AppealRow current,
            String accountId,
            String reason,
            String idempotencyKey
    ) throws SQLException {
        Optional<JdbcWebsiteAppealLifecycleAudit.Entry> found =
                audit.find(connection, current.appealId(), EDIT, idempotencyKey);
        if (found.isEmpty()) return Optional.empty();
        JdbcWebsiteAppealLifecycleAudit.Entry entry = found.orElseThrow();
        JsonNode details = entry.details();
        boolean matches = EDITED_EVENT.equals(entry.eventType())
                && current.caseId().value().equals(entry.caseId())
                && accountId.equals(details.path("accountId").asText())
                && reason.equals(details.path("reason").asText());
        return replayOrConflict(current, matches);
    }

    private Optional<WebsiteAppealMutation> reviewerReplay(
            Connection connection,
            AppealRow current,
            String operation,
            String idempotencyKey,
            String eventType,
            UUID reviewerAccountId,
            String reviewerRank,
            String note
    ) throws SQLException {
        Optional<JdbcWebsiteAppealLifecycleAudit.Entry> found =
                audit.find(connection, current.appealId(), operation, idempotencyKey);
        if (found.isEmpty()) return Optional.empty();
        JdbcWebsiteAppealLifecycleAudit.Entry entry = found.orElseThrow();
        JsonNode details = entry.details();
        boolean matches = eventType.equals(entry.eventType())
                && reviewerAccountId.equals(entry.actorId())
                && current.caseId().value().equals(entry.caseId())
                && reviewerRank.equals(details.path("reviewerRank").asText())
                && nullableTextMatches(note, details.get("note"));
        return replayOrConflict(current, matches);
    }

    private static Optional<WebsiteAppealMutation> replayOrConflict(
            AppealRow current,
            boolean matches
    ) {
        if (!matches) {
            throw conflict("APPEAL_IDEMPOTENCY_CONFLICT", "The appeal key conflicts with prior state");
        }
        return Optional.of(mutation(current, true));
    }

    private static boolean nullableTextMatches(String expected, JsonNode actual) {
        if (expected == null) return actual == null || actual.isNull();
        return actual != null && expected.equals(actual.asText());
    }

    private static Map<String, Object> editDetails(String accountId, String reason, long revision) {
        return Map.of("accountId", accountId, "reason", reason, "revision", revision);
    }

    private static Map<String, Object> reviewerDetails(String rank, String note, long revision) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("reviewerRank", rank);
        details.put("note", note);
        details.put("revision", revision);
        return details;
    }

    private static void updateReason(
            Connection connection,
            AppealRow current,
            String reason,
            long revision,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET appeal_reason = ?, revision = ?, updated_at = ?
                WHERE appeal_id = ? AND revision = ? AND state = 'OPEN'
                  AND reviewer_account_id IS NULL
                """)) {
            statement.setString(1, reason);
            statement.setLong(2, revision);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(current.appealId()));
            statement.setLong(5, current.revision());
            requireSingleUpdate(statement.executeUpdate(), "Website appeal changed during edit");
        }
    }

    private static void updateClaim(
            Connection connection,
            AppealRow current,
            UUID reviewerAccountId,
            String reviewerRank,
            long revision,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET reviewer_account_id = ?, reviewer_rank = ?, revision = ?, updated_at = ?
                WHERE appeal_id = ? AND revision = ? AND state = 'OPEN'
                  AND reviewer_account_id IS NULL
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(reviewerAccountId));
            statement.setString(2, reviewerRank);
            statement.setLong(3, revision);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setBytes(5, UuidBytes.toBytes(current.appealId()));
            statement.setLong(6, current.revision());
            requireSingleUpdate(statement.executeUpdate(), "Website appeal changed during claim");
        }
    }

    private static void updateReopen(
            Connection connection,
            AppealRow current,
            long revision,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_requests
                SET state = 'OPEN', revision = ?, decision_type = NULL,
                    decision_idempotency_key = NULL, reviewer_account_id = NULL,
                    reviewer_rank = NULL, decision_note = NULL, decided_at = NULL,
                    outcome_code = NULL, updated_at = ?
                WHERE appeal_id = ? AND revision = ? AND state IN ('DENIED', 'REJECTED')
                """)) {
            statement.setLong(1, revision);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(current.appealId()));
            statement.setLong(4, current.revision());
            requireSingleUpdate(statement.executeUpdate(), "Website appeal changed during reopen");
        }
    }

    private static AppealRow select(Connection connection, UUID appealId, boolean lock)
            throws SQLException {
        String sql = SELECT_APPEAL + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readAppeal(result) : null;
            }
        }
    }

    private static AppealRow readAppeal(ResultSet result) throws SQLException {
        byte[] reviewer = result.getBytes("reviewer_account_id");
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
                reviewer == null ? null : UuidBytes.fromBytes(reviewer),
                result.getString("decision_note"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getString("sanction_type")
        );
    }

    private static WebsiteAppealMutation mutation(AppealRow row, boolean replayed) {
        return new WebsiteAppealMutation(
                view(row),
                replayed,
                OPEN.equals(row.state()) && row.reviewerAccountId() != null
        );
    }

    private static WebsiteAppealView view(AppealRow row) {
        return new WebsiteAppealView(
                row.appealId(), row.punishmentId(), row.caseId(), row.punishmentType(),
                row.playerUsername(), row.reason(), row.state(), row.revision(),
                row.decisionType(), row.decisionNote(), row.createdAt(), row.updatedAt()
        );
    }

    private static void requireOwner(AppealRow row, String accountId, byte[] accountToken) {
        if (!accountId.equals(row.playerAccountId())
                || !MessageDigest.isEqual(accountToken, row.accountToken())) {
            throw conflict("APPEAL_ACCOUNT_CONFLICT", "The appeal belongs to another account");
        }
    }

    private static AppealRow requireAppeal(AppealRow row) {
        if (row == null || row.playerUsername() == null) {
            throw notFound("APPEAL_NOT_FOUND", "The appeal could not be found");
        }
        return row;
    }

    private static void requireVersion(AppealRow row, long expectedVersion) {
        if (row.revision() != expectedVersion) {
            throw conflict("STALE_APPEAL_STATE", "The appeal changed; reload it and retry");
        }
    }

    private static void requireEditable(AppealRow row) {
        if (!OPEN.equals(row.state()) || row.reviewerAccountId() != null) {
            throw conflict("APPEAL_EDIT_LOCKED", "The appeal can no longer be edited");
        }
    }

    private static void requireClaimable(AppealRow row) {
        if (!OPEN.equals(row.state()) || row.reviewerAccountId() != null) {
            throw conflict("APPEAL_ALREADY_CLAIMED", "The appeal is no longer available to claim");
        }
    }

    private static void requireReopenable(AppealRow row) {
        if (!List.of("DENIED", "REJECTED").contains(row.state())) {
            throw conflict("APPEAL_REOPEN_CONFLICT", "Only a denied or rejected appeal can be reopened");
        }
    }

    private byte[] accountToken(String accountId) {
        try {
            return codeProtector.accountToken(accountId);
        } catch (IllegalArgumentException exception) {
            throw invalid("INVALID_ACCOUNT_ID", "The website account ID is invalid");
        }
    }

    private <T> T transaction(String message, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw persistence(message, exception);
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw persistence(message, exception);
        }
    }

    private static void requireSingleUpdate(int updated, String message) throws SQLException {
        if (updated != EXPECTED_UPDATE_COUNT) throw new SQLException(message);
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

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
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
            UUID reviewerAccountId,
            String decisionNote,
            Instant createdAt,
            Instant updatedAt,
            String punishmentType
    ) {
    }
}
