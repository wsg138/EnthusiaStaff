package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.CaseHistoryDetail;
import net.enthusia.staff.domain.history.HistoryEventType;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;

public final class JdbcModerationHistoryStore implements ModerationHistoryStore {
    private final DataSource dataSource;
    private final CaseReviewStore caseReviews;

    public JdbcModerationHistoryStore(DataSource dataSource, CaseReviewStore caseReviews) {
        if (dataSource == null || caseReviews == null) {
            throw new IllegalArgumentException("history store dependencies must be present");
        }
        this.dataSource = dataSource;
        this.caseReviews = caseReviews;
    }

    @Override
    public ModerationHistoryPage page(
            UUID subjectId,
            int page,
            int pageSize,
            HistoryQueryOptions options
    ) {
        if (subjectId == null || page < 1 || pageSize < 1 || pageSize > 100 || options == null) {
            throw new IllegalArgumentException("history page request is invalid");
        }
        String union = subjectUnion(options);
        byte[] subject = UuidBytes.toBytes(subjectId);
        try (Connection connection = dataSource.getConnection()) {
            long total = count(connection, union, subject);
            int totalPages = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
            if ((total == 0 && page != 1) || (total > 0 && page > totalPages)) {
                throw new IllegalArgumentException("history page exceeds the available range");
            }
            List<ModerationHistoryEntry> entries = total == 0
                    ? List.of()
                    : readPage(
                            connection,
                            union,
                            subject,
                            pageSize,
                            Math.multiplyExact((long) page - 1L, pageSize),
                            options.includeSensitive()
                    );
            return new ModerationHistoryPage(
                    subjectId,
                    page,
                    pageSize,
                    total,
                    totalPages,
                    entries
            );
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read moderation history", exception);
        }
    }

    @Override
    public Optional<CaseHistoryDetail> caseDetail(
            CaseId caseId,
            HistoryQueryOptions options
    ) {
        if (caseId == null || options == null) {
            throw new IllegalArgumentException("case history request is invalid");
        }
        Optional<CaseReview> review = caseReviews.find(caseId);
        if (review.isEmpty()) {
            return Optional.empty();
        }
        String union = caseUnion(options);
        String query = "SELECT * FROM (" + union + ") history "
                + "ORDER BY occurred_at DESC, stable_key DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            bindRepeated(statement, caseId.value(), placeholderCount(union));
            try (ResultSet result = statement.executeQuery()) {
                List<ModerationHistoryEntry> timeline = new ArrayList<>();
                while (result.next()) {
                    timeline.add(readEntry(result, options.includeSensitive()));
                }
                return Optional.of(new CaseHistoryDetail(review.orElseThrow(), timeline));
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read case history", exception);
        }
    }

    private static long count(Connection connection, String union, byte[] subject) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM (" + union + ") history"
        )) {
            bindRepeated(statement, subject, placeholderCount(union));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static List<ModerationHistoryEntry> readPage(
            Connection connection,
            String union,
            byte[] subject,
            int limit,
            long offset,
            boolean includeSensitive
    ) throws SQLException {
        String query = "SELECT * FROM (" + union + ") history "
                + "ORDER BY occurred_at DESC, stable_key DESC LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int repeated = placeholderCount(union);
            bindRepeated(statement, subject, repeated);
            statement.setInt(repeated + 1, limit);
            statement.setLong(repeated + 2, offset);
            try (ResultSet result = statement.executeQuery()) {
                List<ModerationHistoryEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(readEntry(result, includeSensitive));
                }
                return List.copyOf(entries);
            }
        }
    }

    private static ModerationHistoryEntry readEntry(ResultSet result, boolean includeSensitive)
            throws SQLException {
        String caseId = result.getString("case_id");
        String punishmentType = result.getString("punishment_type");
        String actorName = result.getString("actor_name");
        String sensitive = includeSensitive ? result.getString("sensitive_reason") : null;
        return new ModerationHistoryEntry(
                result.getString("stable_key"),
                HistoryEventType.valueOf(result.getString("event_type")),
                result.getTimestamp("occurred_at").toInstant(),
                caseId == null ? Optional.empty() : Optional.of(new CaseId(caseId)),
                optionalUuid(result, "sanction_id"),
                optionalUuid(result, "punishment_request_id"),
                optionalUuid(result, "appeal_id"),
                punishmentType == null ? Optional.empty() : Optional.of(punishmentType),
                result.getString("status"),
                Optional.ofNullable(result.getString("public_reason")).orElse(""),
                optionalInstant(result, "original_expiration"),
                optionalInstant(result, "resulting_expiration"),
                optionalUuid(result, "actor_id"),
                actorName == null || actorName.isBlank() ? Optional.empty() : Optional.of(actorName),
                sensitive == null || sensitive.isBlank() ? Optional.empty() : Optional.of(sensitive)
        );
    }

    private static String subjectUnion(HistoryQueryOptions options) {
        List<String> segments = new ArrayList<>();
        segments.add(caseSegment("c.target_id = ?"));
        segments.add(sanctionCreatedSegment("s.target_id = ?"));
        segments.add(sanctionActivatedSegment("s.target_id = ?"));
        segments.add(naturalExpirationSegment("s.target_id = ?"));
        segments.add(sanctionEventSegment("sanction.target_id = ?"));
        if (options.includeRequestEvents()) {
            segments.add(punishmentRequestSegment("request.target_id = ?"));
        }
        if (options.includeAppealEvents()) {
            segments.add(appealSubmittedSegment("sanction.target_id = ?"));
            segments.add(appealDecisionSegment("sanction.target_id = ?"));
            segments.add(overturnRequestedSegment("c.target_id = ?"));
            segments.add(overturnDecisionSegment("c.target_id = ?"));
        }
        if (options.includeSensitive()) {
            segments.add(staffNoteSegment("note.target_id = ?"));
        }
        return String.join(" UNION ALL ", segments);
    }

    private static String caseUnion(HistoryQueryOptions options) {
        List<String> segments = new ArrayList<>();
        segments.add(caseSegment("c.case_id = ?"));
        segments.add(sanctionCreatedSegment("s.case_id = ?"));
        segments.add(sanctionActivatedSegment("s.case_id = ?"));
        segments.add(naturalExpirationSegment("s.case_id = ?"));
        segments.add(sanctionEventSegment("sanction.case_id = ?"));
        if (options.includeRequestEvents()) {
            segments.add(punishmentRequestSegment("request.resulting_case_id = ?"));
        }
        if (options.includeAppealEvents()) {
            segments.add(appealSubmittedSegment("appeal.case_id = ?"));
            segments.add(appealDecisionSegment("appeal.case_id = ?"));
            segments.add(overturnRequestedSegment("c.case_id = ?"));
            segments.add(overturnDecisionSegment("c.case_id = ?"));
        }
        return String.join(" UNION ALL ", segments);
    }

    private static String caseSegment(String filter) {
        return sql(
                "SELECT CONCAT('case:', c.case_id) AS stable_key,",
                "'CASE_CREATED' AS event_type, c.issued_at AS occurred_at, c.case_id,",
                "NULL AS sanction_id, NULL AS punishment_request_id, NULL AS appeal_id,",
                "c.sanction_family AS punishment_type, c.state AS status, c.public_reason,",
                "NULL AS original_expiration, NULL AS resulting_expiration,",
                "c.actor_id, c.actor_name, c.internal_explanation AS sensitive_reason",
                "FROM cases c WHERE " + filter
        );
    }

    private static String sanctionCreatedSegment(String filter) {
        return sql(
                "SELECT CONCAT('sanction:', HEX(s.sanction_id), ':created') AS stable_key,",
                "'SANCTION_CREATED' AS event_type, s.issued_at AS occurred_at, s.case_id,",
                "s.sanction_id, NULL AS punishment_request_id, NULL AS appeal_id,",
                "s.sanction_type AS punishment_type,",
                "CASE WHEN s.status IN ('PENDING', 'ACTIVE') AND s.expiration_at IS NOT NULL",
                "AND s.expiration_at <= CURRENT_TIMESTAMP(6) THEN 'EXPIRED' ELSE s.status END AS status,",
                "c.public_reason, s.expiration_at AS original_expiration,",
                "s.expiration_at AS resulting_expiration, c.actor_id, c.actor_name,",
                "c.internal_explanation AS sensitive_reason",
                "FROM sanctions s JOIN cases c ON c.case_id = s.case_id WHERE " + filter
        );
    }

    private static String sanctionActivatedSegment(String filter) {
        return sql(
                "SELECT CONCAT('sanction:', HEX(s.sanction_id), ':activated') AS stable_key,",
                "'SANCTION_ACTIVATED' AS event_type, s.activated_at AS occurred_at, s.case_id,",
                "s.sanction_id, NULL AS punishment_request_id, NULL AS appeal_id,",
                "s.sanction_type AS punishment_type, s.status, c.public_reason,",
                "s.expiration_at AS original_expiration, s.expiration_at AS resulting_expiration,",
                "c.actor_id, c.actor_name, NULL AS sensitive_reason",
                "FROM sanctions s JOIN cases c ON c.case_id = s.case_id",
                "WHERE s.activated_at IS NOT NULL AND " + filter
        );
    }

    private static String naturalExpirationSegment(String filter) {
        return sql(
                "SELECT CONCAT('sanction:', HEX(s.sanction_id), ':natural-expiration') AS stable_key,",
                "'SANCTION_EXPIRED' AS event_type, s.expiration_at AS occurred_at, s.case_id,",
                "s.sanction_id, NULL AS punishment_request_id, NULL AS appeal_id,",
                "s.sanction_type AS punishment_type, 'EXPIRED' AS status, c.public_reason,",
                "s.expiration_at AS original_expiration, s.expiration_at AS resulting_expiration,",
                "NULL AS actor_id, NULL AS actor_name, NULL AS sensitive_reason",
                "FROM sanctions s JOIN cases c ON c.case_id = s.case_id",
                "WHERE s.status IN ('PENDING', 'ACTIVE') AND s.expiration_at IS NOT NULL",
                "AND s.expiration_at <= CURRENT_TIMESTAMP(6) AND " + filter
        );
    }

    private static String sanctionEventSegment(String filter) {
        return sql(
                "SELECT CONCAT('sanction-event:', HEX(event.event_id)) AS stable_key,",
                "CASE event.event_type WHEN 'ACTIVATED' THEN 'SANCTION_ACTIVATED'",
                "WHEN 'EXPIRED' THEN 'SANCTION_EXPIRED'",
                "WHEN 'REDUCE_DURATION' THEN 'SANCTION_REDUCED'",
                "WHEN 'REPLACE_EXPIRATION' THEN 'SANCTION_REDUCED'",
                "WHEN 'END_EARLY' THEN 'SANCTION_ENDED_EARLY'",
                "WHEN 'REVOKE' THEN 'SANCTION_REVOKED'",
                "WHEN 'FULL_OVERTURN' THEN 'SANCTION_OVERTURNED'",
                "WHEN 'APPROVE_FULL_OVERTURN' THEN 'SANCTION_OVERTURNED'",
                "ELSE 'SANCTION_CHANGED' END AS event_type,",
                "event.occurred_at, COALESCE(event.case_id, sanction.case_id) AS case_id,",
                "event.sanction_id, event.linked_punishment_request_id AS punishment_request_id,",
                "event.linked_appeal_id AS appeal_id, sanction.sanction_type AS punishment_type,",
                "COALESCE(event.resulting_status, sanction.status) AS status, c.public_reason,",
                "event.previous_expiration AS original_expiration,",
                "COALESCE(event.resulting_expiration, sanction.expiration_at) AS resulting_expiration,",
                "event.actor_id, COALESCE(actor.current_username, c.actor_name) AS actor_name,",
                "event.reason AS sensitive_reason",
                "FROM sanction_events event",
                "JOIN sanctions sanction ON sanction.sanction_id = event.sanction_id",
                "JOIN cases c ON c.case_id = sanction.case_id",
                "LEFT JOIN players actor ON actor.player_id = event.actor_id",
                "WHERE " + filter
        );
    }

    private static String punishmentRequestSegment(String filter) {
        return sql(
                "SELECT CONCAT('punishment-request-event:', HEX(event.event_id)) AS stable_key,",
                "CASE event.event_type WHEN 'SUBMITTED' THEN 'PUNISHMENT_REQUEST_SUBMITTED'",
                "WHEN 'APPROVED' THEN 'PUNISHMENT_REQUEST_APPROVED'",
                "WHEN 'DENIED' THEN 'PUNISHMENT_REQUEST_DENIED'",
                "WHEN 'EXPIRED' THEN 'PUNISHMENT_REQUEST_EXPIRED'",
                "WHEN 'FULFILLED_EXTERNALLY' THEN 'PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY'",
                "ELSE 'PUNISHMENT_REQUEST_CHANGED' END AS event_type,",
                "event.occurred_at, COALESCE(event.resulting_case_id, request.resulting_case_id) AS case_id,",
                "NULL AS sanction_id, request.request_id AS punishment_request_id, NULL AS appeal_id,",
                "request.sanction_family AS punishment_type, request.status, request.public_reason,",
                "NULL AS original_expiration, NULL AS resulting_expiration,",
                "COALESCE(event.actor_id, request.requester_id) AS actor_id,",
                "COALESCE(actor.current_username, request.requester_name) AS actor_name,",
                "CASE WHEN event.note <> '' THEN event.note ELSE request.internal_explanation END AS sensitive_reason",
                "FROM punishment_request_events event",
                "JOIN punishment_requests request ON request.request_id = event.request_id",
                "LEFT JOIN players actor ON actor.player_id = event.actor_id",
                "WHERE event.event_type <> 'LEASE_ACQUIRED' AND " + filter
        );
    }

    private static String appealSubmittedSegment(String filter) {
        return sql(
                "SELECT CONCAT('appeal:', HEX(appeal.appeal_id), ':submitted') AS stable_key,",
                "'APPEAL_SUBMITTED' AS event_type, appeal.created_at AS occurred_at, appeal.case_id,",
                "appeal.punishment_id AS sanction_id, NULL AS punishment_request_id, appeal.appeal_id,",
                "sanction.sanction_type AS punishment_type, appeal.state AS status, c.public_reason,",
                "sanction.expiration_at AS original_expiration,",
                "sanction.expiration_at AS resulting_expiration,",
                "NULL AS actor_id, NULL AS actor_name, NULL AS sensitive_reason",
                "FROM website_appeal_requests appeal",
                "JOIN sanctions sanction ON sanction.sanction_id = appeal.punishment_id",
                "JOIN cases c ON c.case_id = appeal.case_id WHERE " + filter
        );
    }

    private static String appealDecisionSegment(String filter) {
        return sql(
                "SELECT CONCAT('appeal:', HEX(appeal.appeal_id), ':decision') AS stable_key,",
                "'APPEAL_DECIDED' AS event_type, appeal.updated_at AS occurred_at, appeal.case_id,",
                "appeal.punishment_id AS sanction_id, NULL AS punishment_request_id, appeal.appeal_id,",
                "sanction.sanction_type AS punishment_type,",
                "CONCAT(appeal.state, COALESCE(CONCAT(':', appeal.outcome_code), '')) AS status,",
                "c.public_reason, sanction.expiration_at AS original_expiration,",
                "sanction.expiration_at AS resulting_expiration,",
                "NULL AS actor_id, NULL AS actor_name, NULL AS sensitive_reason",
                "FROM website_appeal_requests appeal",
                "JOIN sanctions sanction ON sanction.sanction_id = appeal.punishment_id",
                "JOIN cases c ON c.case_id = appeal.case_id",
                "WHERE appeal.state <> 'PREPARED' AND " + filter
        );
    }

    private static String overturnRequestedSegment(String filter) {
        return sql(
                "SELECT CONCAT('overturn-request:', HEX(request.request_id), ':submitted') AS stable_key,",
                "'OVERTURN_REQUESTED' AS event_type, request.requested_at AS occurred_at, request.case_id,",
                "NULL AS sanction_id, NULL AS punishment_request_id, request.request_id AS appeal_id,",
                "c.sanction_family AS punishment_type, request.state AS status, c.public_reason,",
                "NULL AS original_expiration, NULL AS resulting_expiration, request.requested_by AS actor_id,",
                "actor.current_username AS actor_name, request.explanation AS sensitive_reason",
                "FROM punishment_overturn_requests request",
                "JOIN cases c ON c.case_id = request.case_id",
                "LEFT JOIN players actor ON actor.player_id = request.requested_by",
                "WHERE " + filter
        );
    }

    private static String overturnDecisionSegment(String filter) {
        return sql(
                "SELECT CONCAT('overturn-request:', HEX(request.request_id), ':decision') AS stable_key,",
                "CASE request.state WHEN 'APPROVED' THEN 'OVERTURN_APPROVED'",
                "WHEN 'DENIED' THEN 'OVERTURN_DENIED'",
                "WHEN 'EXPIRED' THEN 'OVERTURN_REQUEST_EXPIRED' ELSE 'APPEAL_CHANGED' END AS event_type,",
                "COALESCE(request.decided_at, request.expires_at) AS occurred_at, request.case_id,",
                "NULL AS sanction_id, NULL AS punishment_request_id, request.request_id AS appeal_id,",
                "c.sanction_family AS punishment_type, request.state AS status, c.public_reason,",
                "NULL AS original_expiration, NULL AS resulting_expiration, request.decided_by AS actor_id,",
                "actor.current_username AS actor_name, request.decision_reason AS sensitive_reason",
                "FROM punishment_overturn_requests request",
                "JOIN cases c ON c.case_id = request.case_id",
                "LEFT JOIN players actor ON actor.player_id = request.decided_by",
                "WHERE request.state <> 'OPEN' AND " + filter
        );
    }

    private static String staffNoteSegment(String filter) {
        return sql(
                "SELECT CONCAT('staff-note:', HEX(note.note_id)) AS stable_key,",
                "'ADMINISTRATIVE_NOTE' AS event_type, note.created_at AS occurred_at, NULL AS case_id,",
                "NULL AS sanction_id, NULL AS punishment_request_id, NULL AS appeal_id,",
                "NULL AS punishment_type, 'RECORDED' AS status, 'Administrative note' AS public_reason,",
                "NULL AS original_expiration, NULL AS resulting_expiration, note.actor_id,",
                "actor.current_username AS actor_name, note.note_text AS sensitive_reason",
                "FROM staff_notes note LEFT JOIN players actor ON actor.player_id = note.actor_id",
                "WHERE " + filter
        );
    }

    private static String sql(String... lines) {
        return String.join("\n", lines);
    }

    private static Optional<UUID> optionalUuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(value));
    }

    private static Optional<Instant> optionalInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static int placeholderCount(String query) {
        int count = 0;
        for (int index = 0; index < query.length(); index++) {
            if (query.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }

    private static void bindRepeated(PreparedStatement statement, byte[] value, int count)
            throws SQLException {
        for (int index = 1; index <= count; index++) {
            statement.setBytes(index, value);
        }
    }

    private static void bindRepeated(PreparedStatement statement, String value, int count)
            throws SQLException {
        for (int index = 1; index <= count; index++) {
            statement.setString(index, value);
        }
    }
}
