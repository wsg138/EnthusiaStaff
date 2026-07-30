package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentMatchKey;
import net.enthusia.staff.domain.application.PunishmentPlan;

final class JdbcPunishmentRequestFulfillment {
    private JdbcPunishmentRequestFulfillment() {
    }

    static int apply(
            Connection connection,
            PunishmentPlan plan,
            CaseId caseId,
            Instant now,
            UUID excludedRequestId
    ) throws SQLException {
        PunishmentMatchKey matchKey = PunishmentMatchKey.of(
                plan.targetId(),
                plan.reasonId(),
                plan.sanctions()
        );
        List<RequestRevision> matches = lockMatches(
                connection,
                matchKey,
                now,
                excludedRequestId
        );
        for (RequestRevision match : matches) {
            resolve(connection, match, plan, caseId, now);
            JdbcPunishmentRequestEvents.fulfilledExternally(
                    connection,
                    match.requestId(),
                    plan.actor().id(),
                    caseId,
                    now
            );
            deleteLease(connection, match.requestId());
        }
        return matches.size();
    }

    private static List<RequestRevision> lockMatches(
            Connection connection,
            PunishmentMatchKey matchKey,
            Instant now,
            UUID excludedRequestId
    ) throws SQLException {
        String sql = "SELECT request_id, revision FROM punishment_requests "
                + "WHERE status = 'PENDING' AND open_match_key = ? AND expires_at > ?"
                + (excludedRequestId == null ? "" : " AND request_id <> ?")
                + " FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, matchKey.value());
            statement.setTimestamp(2, Timestamp.from(now));
            if (excludedRequestId != null) {
                statement.setBytes(3, UuidBytes.toBytes(excludedRequestId));
            }
            try (ResultSet result = statement.executeQuery()) {
                List<RequestRevision> matches = new ArrayList<>();
                while (result.next()) {
                    matches.add(new RequestRevision(
                            UuidBytes.fromBytes(result.getBytes("request_id")),
                            result.getLong("revision")
                    ));
                }
                return List.copyOf(matches);
            }
        }
    }

    private static void resolve(
            Connection connection,
            RequestRevision match,
            PunishmentPlan plan,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_requests
                SET status = 'FULFILLED_EXTERNALLY',
                    revision = revision + 1, resolved_by = ?, resolution_note = ?,
                    resulting_case_id = ?, resolved_at = ?, updated_at = ?
                WHERE request_id = ? AND status = 'PENDING' AND revision = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(plan.actor().id()));
            statement.setString(2, "Exact matching punishment was applied independently");
            statement.setString(3, caseId.value());
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setBytes(6, UuidBytes.toBytes(match.requestId()));
            statement.setLong(7, match.revision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Matching punishment request changed during external fulfillment"
            );
        }
    }

    private static void deleteLease(Connection connection, UUID requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM operation_leases WHERE resource_key = ?")) {
            statement.setString(1, resourceKey(requestId));
            statement.executeUpdate();
        }
    }

    static String resourceKey(UUID requestId) {
        return "punishment-request:" + requestId;
    }

    private record RequestRevision(UUID requestId, long revision) {
    }
}
