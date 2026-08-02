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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.StaffHierarchy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionStatus;

final class JdbcExactSanctionMutationStore implements SanctionMutationStore {
    private static final int PROTOCOL_VERSION = 1;

    private final DataSource dataSource;
    private final ObjectMapper json;
    private final Clock clock;

    JdbcExactSanctionMutationStore(DataSource dataSource, ObjectMapper json, Clock clock) {
        if (dataSource == null || json == null || clock == null) {
            throw new IllegalArgumentException("exact sanction mutation dependencies must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public SanctionChangeResult apply(SanctionChangeRequest request) {
        return new SanctionChangeResult.Rejected(
                "UNSUPPORTED",
                "Case-wide sanction changes are not handled by the exact mutation store"
        );
    }

    @Override
    public OptionalLong exactRevision(UUID sanctionId) {
        if (sanctionId == null) {
            throw new IllegalArgumentException("sanctionId must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT revision FROM sanctions WHERE sanction_id = ?"
             )) {
            statement.setBytes(1, UuidBytes.toBytes(sanctionId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read exact sanction revision", exception);
        }
    }

    @Override
    public ExactSanctionChangeResult applyExact(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits
    ) {
        if (request == null || limits == null) {
            throw new IllegalArgumentException("exact sanction request and limits must be present");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ExactSanctionChangeResult.Applied replay = replay(
                        connection,
                        request.idempotencyKey().value()
                );
                if (replay != null) {
                    connection.rollback();
                    return replay;
                }

                SanctionRow row = lockSanction(connection, request.sanctionId());
                if (row == null) {
                    connection.rollback();
                    return new ExactSanctionChangeResult.Rejected(
                            "SANCTION_NOT_FOUND",
                            "The sanction does not exist"
                    );
                }
                if (row.revision() != request.expectedRevision()) {
                    connection.rollback();
                    return new ExactSanctionChangeResult.Rejected(
                            "STALE_SANCTION_STATE",
                            "The sanction changed after command validation; review its current state and retry"
                    );
                }
                if (!StaffHierarchy.mayMutate(
                        request.actor().rank(),
                        row.issuerRank(),
                        request.bypassHierarchy()
                )) {
                    connection.rollback();
                    return new ExactSanctionChangeResult.Rejected(
                            "HIERARCHY_DENIED",
                            "The sanction was issued outside the actor's mutation hierarchy"
                    );
                }

                ExactSanctionChangeResult linkFailure = validateLinks(connection, request, row);
                if (linkFailure != null) {
                    connection.rollback();
                    return linkFailure;
                }

                Instant now = clock.instant();
                Mutation mutation = calculateMutation(request, limits, row, now);
                if (mutation.noChange() != null) {
                    connection.rollback();
                    return mutation.noChange();
                }
                if (mutation.rejection() != null) {
                    connection.rollback();
                    return mutation.rejection();
                }

                updateSanction(connection, row, mutation);
                if (request.action() == SanctionChangeAction.FULL_OVERTURN) {
                    updateCaseOverturnState(connection, row.caseId());
                }
                insertSanctionEvent(connection, request, row, mutation, now);
                insertAudit(connection, request, row, mutation, now);
                insertOutboxes(connection, request, row, mutation, now);
                connection.commit();
                return applied(request, row, mutation, now, false);
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                ExactSanctionChangeResult.Applied replay = replayAfterConflict(
                        request.idempotencyKey().value()
                );
                if (replay != null) {
                    return replay;
                }
                throw new ModerationPersistenceException(
                        "Exact sanction change transaction failed",
                        exception
                );
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException(
                    "Unable to open exact sanction change transaction",
                    exception
            );
        }
    }

    private ExactSanctionChangeResult validateLinks(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionRow row
    ) throws SQLException {
        if (request.linkedAppealId().isPresent()) {
            UUID appealId = request.linkedAppealId().orElseThrow();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT punishment_id, case_id, state
                    FROM website_appeal_requests
                    WHERE appeal_id = ?
                    FOR UPDATE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(appealId));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return new ExactSanctionChangeResult.Rejected(
                                "APPEAL_NOT_FOUND",
                                "The linked appeal does not exist"
                        );
                    }
                    UUID punishmentId = UuidBytes.fromBytes(result.getBytes("punishment_id"));
                    String caseId = result.getString("case_id");
                    String state = result.getString("state");
                    if (!punishmentId.equals(row.sanctionId()) || !caseId.equals(row.caseId().value())) {
                        return new ExactSanctionChangeResult.Rejected(
                                "APPEAL_TARGET_MISMATCH",
                                "The linked appeal does not belong to this sanction and case"
                        );
                    }
                    if (!"APPLIED".equals(state)) {
                        return new ExactSanctionChangeResult.Rejected(
                                "APPEAL_NOT_ACCEPTED",
                                "The linked appeal has not been accepted"
                        );
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT sanction_id
                    FROM sanction_events
                    WHERE linked_appeal_id = ?
                    FOR UPDATE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(appealId));
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return noChange(
                                "APPEAL_ALREADY_LINKED",
                                "The appeal is already linked to a sanction reversal",
                                row
                        );
                    }
                }
            }
        }

        if (request.linkedPunishmentRequestId().isPresent()) {
            UUID requestId = request.linkedPunishmentRequestId().orElseThrow();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT target_id, resulting_case_id, status
                    FROM punishment_requests
                    WHERE request_id = ?
                    FOR UPDATE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(requestId));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return new ExactSanctionChangeResult.Rejected(
                                "PUNISHMENT_REQUEST_NOT_FOUND",
                                "The linked punishment request does not exist"
                        );
                    }
                    UUID targetId = UuidBytes.fromBytes(result.getBytes("target_id"));
                    String caseId = result.getString("resulting_case_id");
                    String status = result.getString("status");
                    if (!targetId.equals(row.subjectId()) || caseId == null
                            || !caseId.equals(row.caseId().value())) {
                        return new ExactSanctionChangeResult.Rejected(
                                "PUNISHMENT_REQUEST_TARGET_MISMATCH",
                                "The linked punishment request does not belong to this player and case"
                        );
                    }
                    if (!"APPROVED".equals(status) && !"FULFILLED_EXTERNALLY".equals(status)) {
                        return new ExactSanctionChangeResult.Rejected(
                                "PUNISHMENT_REQUEST_NOT_RESOLVED",
                                "The linked punishment request is not resolved"
                        );
                    }
                }
            }
        }
        return null;
    }

    private static Mutation calculateMutation(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits,
            SanctionRow row,
            Instant now
    ) {
        boolean naturallyExpired = row.expiration().isPresent()
                && !row.expiration().orElseThrow().isAfter(now);
        return switch (request.action()) {
            case REDUCE_DURATION -> reduce(request, limits, row, now, naturallyExpired);
            case END_EARLY -> endEarly(row, now, naturallyExpired);
            case REVOKE -> revoke(row, now);
            case FULL_OVERTURN -> overturn(row, now);
            default -> Mutation.rejected(
                    "UNSUPPORTED_ACTION",
                    "The requested exact sanction action is unsupported"
            );
        };
    }

    private static Mutation reduce(
            ExactSanctionChangeRequest request,
            SanctionActionLimits limits,
            SanctionRow row,
            Instant now,
            boolean naturallyExpired
    ) {
        if (!isDurationActive(row.status()) || naturallyExpired) {
            return Mutation.noChange(noChange(
                    "ALREADY_INACTIVE",
                    "The sanction is already inactive and cannot be reduced",
                    row,
                    naturallyExpired ? SanctionStatus.EXPIRED : row.status()
            ));
        }
        Instant replacement = request.replacementExpiration().orElseThrow();
        if (!replacement.isAfter(now) || replacement.isBefore(row.issuedAt())) {
            return Mutation.rejected(
                    "INVALID_EXPIRATION",
                    "The reduced expiration must be after now and not before the original issue time"
            );
        }
        if (row.expiration().isEmpty() && !limits.allowPermanentReduction()) {
            return Mutation.rejected(
                    "PERMANENT_REDUCTION_DENIED",
                    "Current policy does not allow converting a permanent sanction to a finite one"
            );
        }
        if (row.expiration().isPresent()) {
            Instant current = row.expiration().orElseThrow();
            if (replacement.equals(current)) {
                return Mutation.noChange(noChange(
                        "NO_CHANGE",
                        "The sanction already has that expiration",
                        row
                ));
            }
            if (!replacement.isBefore(current)) {
                return Mutation.rejected(
                        "NOT_A_REDUCTION",
                        "A reduction must move the expiration earlier"
                );
            }
        }
        return Mutation.applied(row.status(), Optional.of(replacement), row.endedAt());
    }

    private static Mutation endEarly(SanctionRow row, Instant now, boolean naturallyExpired) {
        if (!isDurationActive(row.status()) || naturallyExpired) {
            return Mutation.noChange(noChange(
                    "ALREADY_INACTIVE",
                    "The sanction is already inactive",
                    row,
                    naturallyExpired ? SanctionStatus.EXPIRED : row.status()
            ));
        }
        return Mutation.applied(
                SanctionStatus.ENDED_EARLY,
                row.expiration(),
                Optional.of(now)
        );
    }

    private static Mutation revoke(SanctionRow row, Instant now) {
        if (row.status() == SanctionStatus.REVOKED) {
            return Mutation.noChange(noChange(
                    "ALREADY_REVOKED",
                    "The sanction is already revoked",
                    row
            ));
        }
        if (row.status() == SanctionStatus.OVERTURNED) {
            return Mutation.rejected(
                    "TERMINAL_STATE_CONFLICT",
                    "An overturned sanction cannot later be revoked"
            );
        }
        return Mutation.applied(
                SanctionStatus.REVOKED,
                row.expiration(),
                Optional.of(now)
        );
    }

    private static Mutation overturn(SanctionRow row, Instant now) {
        if (row.status() == SanctionStatus.OVERTURNED) {
            return Mutation.noChange(noChange(
                    "ALREADY_OVERTURNED",
                    "The sanction is already overturned",
                    row
            ));
        }
        if (row.status() == SanctionStatus.REVOKED) {
            return Mutation.rejected(
                    "TERMINAL_STATE_CONFLICT",
                    "A revoked sanction cannot later be overturned"
            );
        }
        return Mutation.applied(
                SanctionStatus.OVERTURNED,
                row.expiration(),
                Optional.of(now)
        );
    }

    private static boolean isDurationActive(SanctionStatus status) {
        return status == SanctionStatus.PENDING || status == SanctionStatus.ACTIVE;
    }

    private static void updateSanction(
            Connection connection,
            SanctionRow row,
            Mutation mutation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE sanctions
                SET status = ?, expiration_at = ?, ended_at = ?, revision = revision + 1
                WHERE sanction_id = ? AND revision = ?
                """)) {
            statement.setString(1, mutation.resultingStatus().name());
            setInstant(statement, 2, mutation.resultingExpiration());
            setInstant(statement, 3, mutation.resultingEndedAt());
            statement.setBytes(4, UuidBytes.toBytes(row.sanctionId()));
            statement.setLong(5, row.revision());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("locked sanction revision changed unexpectedly");
            }
        }
    }

    private static void updateCaseOverturnState(Connection connection, CaseId caseId) throws SQLException {
        int remaining = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_id, status
                FROM sanctions
                WHERE case_id = ?
                ORDER BY sanction_id
                FOR UPDATE
                """)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!"OVERTURNED".equals(result.getString("status"))) {
                        remaining++;
                    }
                }
            }
        }
        if (remaining != 0) {
            return;
        }
        try (PreparedStatement cases = connection.prepareStatement("""
                UPDATE cases
                SET state = 'FULLY_OVERTURNED', revision = revision + 1
                WHERE case_id = ? AND state <> 'FULLY_OVERTURNED'
                """);
             PreparedStatement step = connection.prepareStatement("""
                UPDATE punishment_steps
                SET escalation_contributes = FALSE
                WHERE case_id = ? AND escalation_contributes = TRUE
                """)) {
            cases.setString(1, caseId.value());
            cases.executeUpdate();
            step.setString(1, caseId.value());
            step.executeUpdate();
        }
    }

    private void insertSanctionEvent(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionRow row,
            Mutation mutation,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(
                    event_id, sanction_id, case_id, subject_id, event_type,
                    previous_status, resulting_status, previous_expiration,
                    resulting_expiration, linked_appeal_id,
                    linked_punishment_request_id, origin_runtime, actor_id,
                    occurred_at, reason, event_json, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(row.sanctionId()));
            statement.setString(3, row.caseId().value());
            statement.setBytes(4, UuidBytes.toBytes(row.subjectId()));
            statement.setString(5, request.action().name());
            statement.setString(6, row.status().name());
            statement.setString(7, mutation.resultingStatus().name());
            setInstant(statement, 8, row.expiration());
            setInstant(statement, 9, mutation.resultingExpiration());
            setUuid(statement, 10, request.linkedAppealId());
            setUuid(statement, 11, request.linkedPunishmentRequestId());
            statement.setString(12, request.originRuntime());
            statement.setBytes(13, UuidBytes.toBytes(request.actor().id()));
            statement.setTimestamp(14, Timestamp.from(now));
            statement.setString(15, truncate(request.reason(), 512));
            statement.setString(16, json.writeValueAsString(eventPayload(request, row, mutation)));
            statement.setString(17, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionRow row,
            Mutation mutation,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(
                    event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(3, UuidBytes.toBytes(request.actor().id()));
            statement.setBytes(4, UuidBytes.toBytes(row.subjectId()));
            statement.setString(5, row.caseId().value());
            statement.setString(6, auditEventType(request.action()));
            statement.setString(7, json.writeValueAsString(eventPayload(request, row, mutation)));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setString(9, request.idempotencyKey().value());
            statement.executeUpdate();
        }
    }

    private void insertOutboxes(
            Connection connection,
            ExactSanctionChangeRequest request,
            SanctionRow row,
            Mutation mutation,
            Instant now
    ) throws SQLException, JsonProcessingException {
        Map<String, Object> payload = eventPayload(request, row, mutation);
        String encoded = json.writeValueAsString(payload);
        try (PreparedStatement network = connection.prepareStatement("""
                INSERT INTO network_outbox(
                    message_id, idempotency_key, destination, message_type,
                    protocol_version, payload_json, available_at, created_at
                ) VALUES (?, ?, 'broadcast', 'SANCTION_CHANGED', ?, ?, ?, ?)
                """);
             PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(
                    message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at
                ) VALUES (?, ?, 'punishments', 'SANCTION_CHANGED', ?, ?, ?)
                """)) {
            network.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            network.setString(2, request.idempotencyKey().value() + ":network");
            network.setInt(3, PROTOCOL_VERSION);
            network.setString(4, encoded);
            network.setTimestamp(5, Timestamp.from(now));
            network.setTimestamp(6, Timestamp.from(now));
            network.executeUpdate();

            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, request.idempotencyKey().value() + ":discord");
            discord.setString(3, encoded);
            discord.setTimestamp(4, Timestamp.from(now));
            discord.setTimestamp(5, Timestamp.from(now));
            discord.executeUpdate();
        }
    }

    private static String auditEventType(SanctionChangeAction action) {
        return switch (action) {
            case REDUCE_DURATION -> "SANCTION_REDUCED";
            case END_EARLY -> "SANCTION_ENDED_EARLY";
            case REVOKE -> "SANCTION_REVOKED";
            case FULL_OVERTURN -> "SANCTION_OVERTURNED";
            default -> "SANCTION_CHANGED";
        };
    }

    private static Map<String, Object> eventPayload(
            ExactSanctionChangeRequest request,
            SanctionRow row,
            Mutation mutation
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", row.caseId().value());
        payload.put("sanctionId", row.sanctionId().toString());
        payload.put("subjectId", row.subjectId().toString());
        payload.put("action", request.action().name());
        payload.put("previousStatus", row.status().name());
        payload.put("resultingStatus", mutation.resultingStatus().name());
        payload.put("previousExpiration", row.expiration().map(Instant::toString).orElse(null));
        payload.put("resultingExpiration", mutation.resultingExpiration().map(Instant::toString).orElse(null));
        payload.put("reason", request.reason());
        payload.put("actorId", request.actor().id().toString());
        payload.put("actorName", request.actor().displayName());
        payload.put("originRuntime", request.originRuntime());
        payload.put("linkedAppealId", request.linkedAppealId().map(UUID::toString).orElse(null));
        payload.put(
                "linkedPunishmentRequestId",
                request.linkedPunishmentRequestId().map(UUID::toString).orElse(null)
        );
        return payload;
    }

    private static SanctionRow lockSanction(Connection connection, UUID sanctionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.sanction_id, s.case_id, s.target_id, s.status, s.issued_at,
                    s.expiration_at, s.ended_at, s.revision, c.actor_rank
                FROM sanctions s
                JOIN cases c ON c.case_id = s.case_id
                WHERE s.sanction_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sanctionId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new SanctionRow(
                        UuidBytes.fromBytes(result.getBytes("sanction_id")),
                        new CaseId(result.getString("case_id")),
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        SanctionStatus.valueOf(result.getString("status")),
                        result.getTimestamp("issued_at").toInstant(),
                        optionalInstant(result, "expiration_at"),
                        optionalInstant(result, "ended_at"),
                        result.getLong("revision"),
                        issuerRank(result.getString("actor_rank"))
                );
            }
        }
    }

    private static StaffRank issuerRank(String stored) {
        if (stored == null || stored.isBlank()) {
            return StaffRank.SYSTEM;
        }
        if (stored.equalsIgnoreCase("OWNER")) {
            return StaffRank.FOUNDER;
        }
        try {
            return StaffRank.valueOf(stored.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return StaffRank.SYSTEM;
        }
    }

    private ExactSanctionChangeResult.Applied replay(Connection connection, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event.case_id, event.sanction_id, event.subject_id, event.event_type,
                    event.previous_status, event.resulting_status, event.previous_expiration,
                    event.resulting_expiration, event.occurred_at, event.linked_appeal_id,
                    event.linked_punishment_request_id
                FROM sanction_events event
                WHERE event.idempotency_key = ?
                """)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new ExactSanctionChangeResult.Applied(
                        new CaseId(result.getString("case_id")),
                        UuidBytes.fromBytes(result.getBytes("sanction_id")),
                        UuidBytes.fromBytes(result.getBytes("subject_id")),
                        SanctionChangeAction.valueOf(result.getString("event_type")),
                        SanctionStatus.valueOf(result.getString("previous_status")),
                        SanctionStatus.valueOf(result.getString("resulting_status")),
                        optionalInstant(result, "previous_expiration"),
                        optionalInstant(result, "resulting_expiration"),
                        result.getTimestamp("occurred_at").toInstant(),
                        optionalUuid(result, "linked_appeal_id"),
                        optionalUuid(result, "linked_punishment_request_id"),
                        true
                );
            }
        }
    }

    private ExactSanctionChangeResult.Applied replayAfterConflict(String key) {
        try (Connection connection = dataSource.getConnection()) {
            return replay(connection, key);
        } catch (SQLException exception) {
            return null;
        }
    }

    private static ExactSanctionChangeResult.Applied applied(
            ExactSanctionChangeRequest request,
            SanctionRow row,
            Mutation mutation,
            Instant now,
            boolean replayed
    ) {
        return new ExactSanctionChangeResult.Applied(
                row.caseId(),
                row.sanctionId(),
                row.subjectId(),
                request.action(),
                row.status(),
                mutation.resultingStatus(),
                row.expiration(),
                mutation.resultingExpiration(),
                now,
                request.linkedAppealId(),
                request.linkedPunishmentRequestId(),
                replayed
        );
    }

    private static ExactSanctionChangeResult.NoChange noChange(
            String code,
            String message,
            SanctionRow row
    ) {
        return noChange(code, message, row, row.status());
    }

    private static ExactSanctionChangeResult.NoChange noChange(
            String code,
            String message,
            SanctionRow row,
            SanctionStatus currentStatus
    ) {
        return new ExactSanctionChangeResult.NoChange(
                code,
                message,
                row.caseId(),
                row.sanctionId(),
                currentStatus,
                row.expiration()
        );
    }

    private static Optional<Instant> optionalInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static Optional<UUID> optionalUuid(ResultSet result, String column) throws SQLException {
        byte[] value = result.getBytes(column);
        return value == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(value));
    }

    private static void setInstant(
            PreparedStatement statement,
            int index,
            Optional<Instant> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        }
    }

    private static void setUuid(
            PreparedStatement statement,
            int index,
            Optional<UUID> value
    ) throws SQLException {
        if (value.isPresent()) {
            statement.setBytes(index, UuidBytes.toBytes(value.orElseThrow()));
        } else {
            statement.setNull(index, java.sql.Types.BINARY);
        }
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
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

    private record SanctionRow(
            UUID sanctionId,
            CaseId caseId,
            UUID subjectId,
            SanctionStatus status,
            Instant issuedAt,
            Optional<Instant> expiration,
            Optional<Instant> endedAt,
            long revision,
            StaffRank issuerRank
    ) {
    }

    private record Mutation(
            SanctionStatus resultingStatus,
            Optional<Instant> resultingExpiration,
            Optional<Instant> resultingEndedAt,
            ExactSanctionChangeResult.NoChange noChange,
            ExactSanctionChangeResult.Rejected rejection
    ) {
        private static Mutation applied(
                SanctionStatus status,
                Optional<Instant> expiration,
                Optional<Instant> endedAt
        ) {
            return new Mutation(status, expiration, endedAt, null, null);
        }

        private static Mutation noChange(ExactSanctionChangeResult.NoChange result) {
            return new Mutation(null, Optional.empty(), Optional.empty(), result, null);
        }

        private static Mutation rejected(String code, String message) {
            return new Mutation(
                    null,
                    Optional.empty(),
                    Optional.empty(),
                    null,
                    new ExactSanctionChangeResult.Rejected(code, message)
            );
        }
    }
}
