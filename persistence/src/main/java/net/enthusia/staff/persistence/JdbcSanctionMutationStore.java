package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;

public final class JdbcSanctionMutationStore implements SanctionMutationStore {
    private static final int PROTOCOL_VERSION = 1;

    private final DataSource dataSource;
    private final ObjectMapper json;
    private final Clock clock;

    public JdbcSanctionMutationStore(DataSource dataSource, ObjectMapper json, Clock clock) {
        if (dataSource == null || json == null || clock == null) {
            throw new IllegalArgumentException("sanction mutation dependencies must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public SanctionChangeResult apply(SanctionChangeRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (isReplay(connection, request.idempotencyKey().value())) {
                    connection.rollback();
                    return new SanctionChangeResult.Applied(0, true);
                }
                CaseRow caseRow = lockCase(connection, request.caseId().value());
                if (caseRow == null) {
                    connection.rollback();
                    return new SanctionChangeResult.Rejected("CASE_NOT_FOUND", "The case does not exist");
                }
                Instant now = clock.instant();
                expireOverturnRequest(connection, request.caseId().value(), now);
                SanctionChangeResult.Rejected stale = validateExpectation(connection, request, caseRow, now);
                if (stale != null) {
                    connection.rollback();
                    return stale;
                }
                Change change = applyChange(connection, request, now);
                if (change.rejection() != null) {
                    connection.rollback();
                    return change.rejection();
                }
                insertSanctionEvents(connection, request, change.sanctionIds(), now);
                insertAudit(connection, request, caseRow.targetId(), change.sanctionIds(), now);
                insertOutboxes(connection, request, caseRow.targetId(), now);
                connection.commit();
                return new SanctionChangeResult.Applied(change.sanctionIds().size(), false);
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                if (isReplayAfterConflict(request.idempotencyKey().value())) {
                    return new SanctionChangeResult.Applied(0, true);
                }
                throw new ModerationPersistenceException("Sanction change transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open sanction change transaction", exception);
        }
    }

    private Change applyChange(
            Connection connection,
            SanctionChangeRequest request,
            Instant now
    ) throws SQLException {
        return switch (request.action()) {
            case END_EARLY -> changeSanctions(connection, request, "ENDED_EARLY", now, false);
            case REVOKE -> changeSanctions(connection, request, "REVOKED", now, true);
            case REDUCE_DURATION -> changeExpiration(connection, request, now, true);
            case REPLACE_EXPIRATION -> changeExpiration(connection, request, now, false);
            case FULL_OVERTURN -> fullyOverturn(connection, request, now);
            case REMOVE_ESCALATION_CONTRIBUTION -> contribution(connection, request, false);
            case RESTORE_ESCALATION_CONTRIBUTION -> contribution(connection, request, true);
            case REQUEST_FULL_OVERTURN -> requestOverturn(connection, request, now);
            case APPROVE_FULL_OVERTURN -> decideOverturn(connection, request, now, true);
            case DENY_FULL_OVERTURN -> decideOverturn(connection, request, now, false);
        };
    }

    private static SanctionChangeResult.Rejected validateExpectation(
            Connection connection,
            SanctionChangeRequest request,
            CaseRow caseRow,
            Instant now
    ) throws SQLException {
        if (request.expectation().isEmpty()) {
            return null;
        }
        net.enthusia.staff.domain.sanction.SanctionChangeExpectation expected =
                request.expectation().orElseThrow();
        Map<UUID, Long> sanctionRevisions = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_id, revision FROM sanctions WHERE case_id = ? FOR UPDATE
                """)) {
            statement.setString(1, request.caseId().value());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    sanctionRevisions.put(
                            UuidBytes.fromBytes(result.getBytes("sanction_id")),
                            result.getLong("revision")
                    );
                }
            }
        }
        Optional<Boolean> contribution;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT escalation_contributes FROM punishment_steps WHERE case_id = ? FOR UPDATE
                """)) {
            statement.setString(1, request.caseId().value());
            try (ResultSet result = statement.executeQuery()) {
                contribution = result.next()
                        ? Optional.of(result.getBoolean("escalation_contributes"))
                        : Optional.empty();
            }
        }
        Optional<UUID> openRequest = Optional.ofNullable(openRequest(
                connection, request.caseId().value(), now
        ));
        if (caseRow.revision() != expected.caseRevision()
                || !sanctionRevisions.equals(expected.sanctionRevisions())
                || !contribution.equals(expected.escalationContributes())
                || !openRequest.equals(expected.openOverturnRequestId())) {
            return new SanctionChangeResult.Rejected(
                    "STALE_CASE",
                    "The case or its sanctions changed after review; reopen it before confirming"
            );
        }
        return null;
    }

    private static Change changeSanctions(
            Connection connection,
            SanctionChangeRequest request,
            String status,
            Instant now,
            boolean includeApplied
    ) throws SQLException {
        List<UUID> sanctions = lockChangeableSanctions(
                connection,
                request.caseId().value(),
                includeApplied
        );
        if (sanctions.isEmpty()) {
            return Change.rejected("NO_ACTIVE_SANCTIONS", "The case has no active sanctions to change");
        }
        try (PreparedStatement statement = includeApplied
                ? connection.prepareStatement("""
                        UPDATE sanctions SET status = ?, ended_at = ?, revision = revision + 1
                        WHERE case_id = ? AND status IN ('PENDING', 'ACTIVE', 'APPLIED')
                        """)
                : connection.prepareStatement("""
                        UPDATE sanctions SET status = ?, ended_at = ?, revision = revision + 1
                        WHERE case_id = ? AND status IN ('PENDING', 'ACTIVE')
                        """)) {
            statement.setString(1, status);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, request.caseId().value());
            statement.executeUpdate();
        }
        return Change.applied(sanctions);
    }

    private static Change changeExpiration(
            Connection connection,
            SanctionChangeRequest request,
            Instant now,
            boolean reductionOnly
    ) throws SQLException {
        Instant replacement = request.replacementExpiration().orElseThrow();
        if (!replacement.isAfter(now)) {
            return Change.rejected("INVALID_EXPIRATION", "The replacement expiration must be in the future");
        }
        List<UUID> sanctions = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT sanction_id, expiration_at FROM sanctions
                WHERE case_id = ? AND status = 'ACTIVE' FOR UPDATE
                """)) {
            select.setString(1, request.caseId().value());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    Timestamp current = result.getTimestamp("expiration_at");
                    if (reductionOnly && current != null && !replacement.isBefore(current.toInstant())) {
                        return Change.rejected(
                                "NOT_A_REDUCTION", "A reduced expiration must precede every current expiration"
                        );
                    }
                    sanctions.add(UuidBytes.fromBytes(result.getBytes("sanction_id")));
                }
            }
        }
        if (sanctions.isEmpty()) {
            return Change.rejected("NO_ACTIVE_SANCTIONS", "The case has no active sanctions to change");
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE sanctions SET expiration_at = ?, revision = revision + 1
                WHERE case_id = ? AND status = 'ACTIVE'
                """)) {
            update.setTimestamp(1, Timestamp.from(replacement));
            update.setString(2, request.caseId().value());
            update.executeUpdate();
        }
        return Change.applied(sanctions);
    }

    private static Change fullyOverturn(
            Connection connection,
            SanctionChangeRequest request,
            Instant now
    ) throws SQLException {
        List<UUID> sanctions = lockAllSanctions(connection, request.caseId().value());
        try (PreparedStatement cases = connection.prepareStatement("""
                UPDATE cases SET state = 'FULLY_OVERTURNED', revision = revision + 1 WHERE case_id = ?
                """);
             PreparedStatement step = connection.prepareStatement("""
                UPDATE punishment_steps SET escalation_contributes = FALSE WHERE case_id = ?
                """);
             PreparedStatement updates = connection.prepareStatement("""
                UPDATE sanctions SET status = 'OVERTURNED', ended_at = ?, revision = revision + 1
                WHERE case_id = ? AND status <> 'OVERTURNED'
                """)) {
            cases.setString(1, request.caseId().value());
            cases.executeUpdate();
            step.setString(1, request.caseId().value());
            step.executeUpdate();
            updates.setTimestamp(1, Timestamp.from(now));
            updates.setString(2, request.caseId().value());
            updates.executeUpdate();
        }
        return Change.applied(sanctions);
    }

    private static Change contribution(
            Connection connection,
            SanctionChangeRequest request,
            boolean contributes
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_steps SET escalation_contributes = ?
                WHERE case_id = ? AND escalation_contributes <> ?
                """)) {
            statement.setBoolean(1, contributes);
            statement.setString(2, request.caseId().value());
            statement.setBoolean(3, contributes);
            if (statement.executeUpdate() == 0) {
                return Change.rejected("NO_CHANGE", "The escalation contribution already has that state");
            }
        }
        return Change.applied(List.of());
    }

    private static Change requestOverturn(
            Connection connection,
            SanctionChangeRequest request,
            Instant now
    ) throws SQLException {
        try (PreparedStatement existing = connection.prepareStatement("""
                SELECT 1 FROM punishment_overturn_requests
                WHERE case_id = ? AND state = 'OPEN' FOR UPDATE
                """)) {
            existing.setString(1, request.caseId().value());
            try (ResultSet result = existing.executeQuery()) {
                if (result.next()) {
                    return Change.rejected("REQUEST_ALREADY_OPEN", "The case already has an open overturn request");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_overturn_requests(request_id, case_id, requested_by, explanation,
                    state, requested_at, expires_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, request.caseId().value());
            statement.setBytes(3, UuidBytes.toBytes(request.actor().id()));
            statement.setString(4, request.reason());
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now.plus(7, ChronoUnit.DAYS)));
            statement.executeUpdate();
        }
        return Change.applied(List.of());
    }

    private static Change decideOverturn(
            Connection connection,
            SanctionChangeRequest request,
            Instant now,
            boolean approved
    ) throws SQLException {
        UUID requestId = openRequest(connection, request.caseId().value(), now);
        if (requestId == null) {
            return Change.rejected("NO_OPEN_REQUEST", "The case has no unexpired open overturn request");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_overturn_requests
                SET state = ?, decided_by = ?, decided_at = ?, decision_reason = ?
                WHERE request_id = ? AND state = 'OPEN'
                """)) {
            statement.setString(1, approved ? "APPROVED" : "DENIED");
            statement.setBytes(2, UuidBytes.toBytes(request.actor().id()));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setString(4, request.reason());
            statement.setBytes(5, UuidBytes.toBytes(requestId));
            statement.executeUpdate();
        }
        return approved ? fullyOverturn(connection, request, now) : Change.applied(List.of());
    }

    private static UUID openRequest(Connection connection, String caseId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT request_id FROM punishment_overturn_requests
                WHERE case_id = ? AND state = 'OPEN' AND expires_at > ? FOR UPDATE
                """)) {
            statement.setString(1, caseId);
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UuidBytes.fromBytes(result.getBytes(1)) : null;
            }
        }
    }

    private static void expireOverturnRequest(Connection connection, String caseId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_overturn_requests SET state = 'EXPIRED'
                WHERE case_id = ? AND state = 'OPEN' AND expires_at <= ?
                """)) {
            statement.setString(1, caseId);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static List<UUID> lockAllSanctions(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_id FROM sanctions WHERE case_id = ? FOR UPDATE
                """)) {
            return lockedSanctions(statement, caseId);
        }
    }

    private static List<UUID> lockChangeableSanctions(
            Connection connection,
            String caseId,
            boolean includeApplied
    ) throws SQLException {
        try (PreparedStatement statement = includeApplied
                ? connection.prepareStatement("""
                        SELECT sanction_id FROM sanctions
                        WHERE case_id = ? AND status IN ('PENDING', 'ACTIVE', 'APPLIED')
                        FOR UPDATE
                        """)
                : connection.prepareStatement("""
                        SELECT sanction_id FROM sanctions
                        WHERE case_id = ? AND status IN ('PENDING', 'ACTIVE')
                        FOR UPDATE
                        """)) {
            return lockedSanctions(statement, caseId);
        }
    }

    private static List<UUID> lockedSanctions(PreparedStatement statement, String caseId)
            throws SQLException {
        statement.setString(1, caseId);
        try (ResultSet result = statement.executeQuery()) {
            List<UUID> sanctions = new ArrayList<>();
            while (result.next()) {
                sanctions.add(UuidBytes.fromBytes(result.getBytes(1)));
            }
            return List.copyOf(sanctions);
        }
    }

    private static CaseRow lockCase(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT target_id, state, revision FROM cases WHERE case_id = ? FOR UPDATE
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new CaseRow(
                                UuidBytes.fromBytes(result.getBytes("target_id")),
                                result.getString("state"),
                                result.getLong("revision")
                        )
                        : null;
            }
        }
    }

    private void insertSanctionEvents(
            Connection connection,
            SanctionChangeRequest request,
            List<UUID> sanctions,
            Instant now
    ) throws SQLException, JsonProcessingException {
        if (sanctions.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id,
                    occurred_at, reason, event_json, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < sanctions.size(); index++) {
                statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                statement.setBytes(2, UuidBytes.toBytes(sanctions.get(index)));
                statement.setString(3, request.action().name());
                statement.setBytes(4, UuidBytes.toBytes(request.actor().id()));
                statement.setTimestamp(5, Timestamp.from(now));
                statement.setString(6, truncate(request.reason(), 512));
                statement.setString(7, json.writeValueAsString(Map.of("action", request.action().name())));
                statement.setString(8, derivedIdempotencyKey(request.idempotencyKey().value(), Integer.toString(index)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertAudit(
            Connection connection,
            SanctionChangeRequest request,
            UUID targetId,
            List<UUID> sanctions,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, 'SANCTION_CHANGED', 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(3, UuidBytes.toBytes(request.actor().id()));
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, request.caseId().value());
            statement.setString(6, json.writeValueAsString(Map.of(
                    "action", request.action().name(),
                    "reason", request.reason(),
                    "sanctionIds", sanctions.stream().map(UUID::toString).toList()
            )));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setString(8, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private void insertOutboxes(
            Connection connection,
            SanctionChangeRequest request,
            UUID targetId,
            Instant now
    ) throws SQLException, JsonProcessingException {
        String payload = json.writeValueAsString(Map.of(
                "caseId", request.caseId().value(),
                "targetId", targetId.toString(),
                "action", request.action().name()
        ));
        try (PreparedStatement network = connection.prepareStatement("""
                INSERT INTO network_outbox(message_id, idempotency_key, destination, message_type,
                    protocol_version, payload_json, available_at, created_at)
                VALUES (?, ?, 'broadcast', 'SANCTION_CHANGED', ?, ?, ?, ?)
                """);
             PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'punishments', 'SANCTION_CHANGED', ?, ?, ?)
                """)) {
            network.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            network.setString(2, request.idempotencyKey().value() + ":network");
            network.setInt(3, PROTOCOL_VERSION);
            network.setString(4, payload);
            network.setTimestamp(5, Timestamp.from(now));
            network.setTimestamp(6, Timestamp.from(now));
            network.executeUpdate();

            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, request.idempotencyKey().value() + ":discord");
            discord.setString(3, payload);
            discord.setTimestamp(4, Timestamp.from(now));
            discord.setTimestamp(5, Timestamp.from(now));
            discord.executeUpdate();
        }
    }

    private static boolean isReplay(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM audit_events WHERE idempotency_key = ?")) {
            statement.setString(1, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean isReplayAfterConflict(String idempotencyKey) {
        try (Connection connection = dataSource.getConnection()) {
            return isReplay(connection, idempotencyKey);
        } catch (SQLException exception) {
            return false;
        }
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String derivedIdempotencyKey(String value, String suffix) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((value + '\u0000' + suffix).getBytes(StandardCharsets.UTF_8));
            return "derived:" + java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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
            // Closing returns the connection to the pool; the original failure remains authoritative.
        }
    }

    private record CaseRow(UUID targetId, String state, long revision) {
    }

    private record Change(List<UUID> sanctionIds, SanctionChangeResult.Rejected rejection) {
        private Change {
            sanctionIds = List.copyOf(sanctionIds);
        }

        private static Change applied(List<UUID> sanctions) {
            return new Change(sanctions, null);
        }

        private static Change rejected(String code, String message) {
            return new Change(List.of(), new SanctionChangeResult.Rejected(code, message));
        }
    }
}
