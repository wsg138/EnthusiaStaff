package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentMatchKey;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

public final class JdbcPunishmentRequestStore implements PunishmentRequestStore {
    private static final int MAXIMUM_EXPIRATIONS_PER_RUN = 1_000;
    private static final String COLUMNS = """
            request_id, submission_key, match_key, target_id, requester_id, requester_name,
            requester_rank, reason_id, sanction_family, public_reason, internal_explanation,
            configuration_version, visibility, required_rank, raw_ordinal, effective_ordinal,
            selected_ordinal, recency_bonus, step_label, contribution_json, sanctions_json,
            status, revision, resolved_by, resolution_note, resulting_case_id,
            created_at, updated_at, expires_at, resolved_at
            """;

    private final DataSource dataSource;
    private final ObjectMapper json;
    private final JdbcPunishmentRequestCodec codec;
    private final JdbcModerationStore moderation;

    public JdbcPunishmentRequestStore(
            DataSource dataSource,
            ObjectMapper json,
            JdbcModerationStore moderation
    ) {
        if (dataSource == null || json == null || moderation == null) {
            throw new IllegalArgumentException("dataSource, json and moderation store must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
        this.codec = new JdbcPunishmentRequestCodec(json);
        this.moderation = moderation;
    }

    @Override
    public PunishmentRequestResult.Submitted submit(PunishmentApprovalRequest request) {
        if (request == null || request.status() != PunishmentRequestStatus.PENDING) {
            throw new IllegalArgumentException("a pending punishment request must be present");
        }
        try {
            return JdbcTransactionSupport.execute(
                    dataSource,
                    "Unable to submit punishment request",
                    connection -> submit(connection, request)
            );
        } catch (ModerationPersistenceException exception) {
            PunishmentApprovalRequest replay = replayAfterConflict(request);
            if (replay != null) {
                return new PunishmentRequestResult.Submitted(replay, true);
            }
            throw exception;
        }
    }

    @Override
    public Optional<PunishmentApprovalRequest> find(UUID requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("punishment request identifier must be present");
        }
        String sql = "SELECT " + COLUMNS + " FROM punishment_requests WHERE request_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(codec.read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read punishment request", exception);
        }
    }

    @Override
    public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("current time and a limit between 1 and 500 are required");
        }
        String sql = "SELECT " + COLUMNS + " FROM punishment_requests "
                + "WHERE status = 'PENDING' AND expires_at > ? ORDER BY created_at LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PunishmentApprovalRequest> pending = new ArrayList<>();
                while (result.next()) {
                    pending.add(codec.read(result));
                }
                return List.copyOf(pending);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to list pending punishment requests", exception);
        }
    }

    @Override
    public Optional<PunishmentApprovalLease> acquire(
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    ) {
        if (requestId == null || ownerId == null || now == null || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("valid punishment approval lease fields must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to acquire punishment approval lease",
                connection -> acquire(connection, requestId, ownerId, now, leaseExpiresAt)
        );
    }

    @Override
    public PunishmentRequestResult approve(
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId caseId,
            Instant now
    ) {
        validateDecision(lease, approver, now);
        if (caseId == null) {
            throw new IllegalArgumentException("resulting case identifier must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to approve punishment request",
                connection -> approve(connection, lease, approver, caseId, now)
        );
    }

    @Override
    public PunishmentRequestResult deny(
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    ) {
        validateDecision(lease, approver, now);
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("punishment request denial note must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to deny punishment request",
                connection -> deny(connection, lease, approver, note, now)
        );
    }

    @Override
    public int expire(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to expire punishment requests",
                connection -> expire(connection, now)
        );
    }

    private PunishmentRequestResult.Submitted submit(
            Connection connection,
            PunishmentApprovalRequest request
    ) throws SQLException {
        PunishmentApprovalRequest replay = existingForSubmission(
                connection,
                request.submissionKey().value(),
                request.proposal().matchKey().value(),
                true
        );
        if (replay != null) {
            return new PunishmentRequestResult.Submitted(replay, true);
        }
        ensureTarget(connection, request.proposal().targetId(), request.createdAt());
        insertRequest(connection, request);
        insertEvent(
                connection,
                request.requestId(),
                "SUBMITTED",
                request.proposal().requester().id(),
                null,
                null,
                "Punishment request submitted",
                request.createdAt()
        );
        insertNotifications(connection, request);
        return new PunishmentRequestResult.Submitted(request, false);
    }

    private Optional<PunishmentApprovalLease> acquire(
            Connection connection,
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    ) throws SQLException {
        PunishmentApprovalRequest request = lock(connection, requestId);
        if (request == null || !request.pendingAt(now)) {
            return Optional.empty();
        }
        String resourceKey = resourceKey(requestId);
        long fence = JdbcOperationLeaseSupport.acquire(
                connection,
                resourceKey,
                ownerId,
                leaseExpiresAt,
                now
        );
        if (fence == JdbcOperationLeaseSupport.UNAVAILABLE) {
            return Optional.empty();
        }
        insertEvent(
                connection,
                requestId,
                "LEASE_ACQUIRED",
                ownerId,
                fence,
                null,
                "Punishment request review lease acquired",
                now
        );
        return Optional.of(new PunishmentApprovalLease(request, ownerId, fence, leaseExpiresAt));
    }

    private PunishmentRequestResult approve(
            Connection connection,
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId proposedCaseId,
            Instant now
    ) throws SQLException {
        PunishmentApprovalRequest current = lock(connection, lease.request().requestId());
        if (current == null) {
            return rejected("REQUEST_NOT_FOUND", "The punishment request does not exist");
        }
        if (current.status() == PunishmentRequestStatus.APPROVED && current.resultingCaseId() != null) {
            return new PunishmentRequestResult.Approved(current, current.resultingCaseId(), true);
        }
        PunishmentRequestResult.Rejected stateIssue = decisionStateIssue(
                connection,
                current,
                lease,
                approver,
                now
        );
        if (stateIssue != null) {
            return stateIssue;
        }
        PunishmentPlan plan = current.proposal().toPlan(
                proposedCaseId,
                approvalIdempotency(current.requestId()),
                now
        );
        PunishmentResult.Accepted accepted = moderation.createPunishment(connection, plan);
        PunishmentApprovalRequest approved = resolve(
                connection,
                current,
                PunishmentRequestStatus.APPROVED,
                approver.id(),
                "Approved by " + approver.displayName(),
                accepted.caseId(),
                now
        );
        insertEvent(
                connection,
                current.requestId(),
                "APPROVED",
                approver.id(),
                lease.fenceToken(),
                accepted.caseId(),
                "Punishment request approved and committed",
                now
        );
        JdbcOperationLeaseSupport.release(
                connection,
                resourceKey(current.requestId()),
                approver.id(),
                lease.fenceToken()
        );
        fulfillMatching(connection, plan, accepted.caseId(), now, current.requestId());
        return new PunishmentRequestResult.Approved(approved, accepted.caseId(), accepted.replayed());
    }

    private PunishmentRequestResult deny(
            Connection connection,
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    ) throws SQLException {
        PunishmentApprovalRequest current = lock(connection, lease.request().requestId());
        if (current == null) {
            return rejected("REQUEST_NOT_FOUND", "The punishment request does not exist");
        }
        if (current.status() == PunishmentRequestStatus.DENIED) {
            return new PunishmentRequestResult.Denied(current, true);
        }
        PunishmentRequestResult.Rejected stateIssue = decisionStateIssue(
                connection,
                current,
                lease,
                approver,
                now
        );
        if (stateIssue != null) {
            return stateIssue;
        }
        PunishmentApprovalRequest denied = resolve(
                connection,
                current,
                PunishmentRequestStatus.DENIED,
                approver.id(),
                note,
                null,
                now
        );
        insertEvent(
                connection,
                current.requestId(),
                "DENIED",
                approver.id(),
                lease.fenceToken(),
                null,
                note,
                now
        );
        JdbcOperationLeaseSupport.release(
                connection,
                resourceKey(current.requestId()),
                approver.id(),
                lease.fenceToken()
        );
        return new PunishmentRequestResult.Denied(denied, false);
    }

    private int expire(Connection connection, Instant now) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM punishment_requests "
                + "WHERE status = 'PENDING' AND expires_at <= ? "
                + "ORDER BY expires_at LIMIT ? FOR UPDATE";
        List<PunishmentApprovalRequest> expired = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, MAXIMUM_EXPIRATIONS_PER_RUN);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    expired.add(codec.read(result));
                }
            }
        }
        for (PunishmentApprovalRequest request : expired) {
            resolve(
                    connection,
                    request,
                    PunishmentRequestStatus.EXPIRED,
                    null,
                    "Punishment request expired without a decision",
                    null,
                    now
            );
            insertEvent(
                    connection,
                    request.requestId(),
                    "EXPIRED",
                    null,
                    null,
                    null,
                    "Punishment request expired without a decision",
                    now
            );
            deleteLease(connection, request.requestId());
        }
        return expired.size();
    }

    private PunishmentRequestResult.Rejected decisionStateIssue(
            Connection connection,
            PunishmentApprovalRequest current,
            PunishmentApprovalLease lease,
            Actor approver,
            Instant now
    ) throws SQLException {
        if (!current.pendingAt(now)) {
            return rejected("REQUEST_NOT_PENDING", "The punishment request is resolved or expired");
        }
        if (current.revision() != lease.request().revision()) {
            return rejected("STALE_REQUEST", "The punishment request changed after the lease was acquired");
        }
        boolean holdsLease = lease.ownerId().equals(approver.id()) && JdbcOperationLeaseSupport.holds(
                connection,
                resourceKey(current.requestId()),
                approver.id(),
                lease.fenceToken(),
                now
        );
        if (!holdsLease) {
            return rejected("STALE_LEASE", "The punishment approval lease is stale or belongs to another reviewer");
        }
        return null;
    }

    private PunishmentApprovalRequest resolve(
            Connection connection,
            PunishmentApprovalRequest current,
            PunishmentRequestStatus status,
            UUID resolvedBy,
            String note,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE punishment_requests
                SET status = ?, revision = revision + 1, resolved_by = ?, resolution_note = ?,
                    resulting_case_id = ?, resolved_at = ?, updated_at = ?
                WHERE request_id = ? AND status = 'PENDING' AND revision = ?
                """)) {
            statement.setString(1, status.name());
            if (resolvedBy == null) {
                statement.setNull(2, java.sql.Types.BINARY);
            } else {
                statement.setBytes(2, UuidBytes.toBytes(resolvedBy));
            }
            statement.setString(3, note);
            if (caseId == null) {
                statement.setNull(4, java.sql.Types.CHAR);
            } else {
                statement.setString(4, caseId.value());
            }
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setBytes(7, UuidBytes.toBytes(current.requestId()));
            statement.setLong(8, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request state changed during resolution"
            );
        }
        return new PunishmentApprovalRequest(
                current.requestId(),
                current.submissionKey(),
                current.proposal(),
                current.createdAt(),
                current.expiresAt(),
                status,
                current.revision() + 1,
                resolvedBy,
                note,
                caseId,
                now
        );
    }

    private PunishmentApprovalRequest existingForSubmission(
            Connection connection,
            String submissionKey,
            String matchKey,
            boolean lock
    ) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM punishment_requests "
                + "WHERE submission_key = ? OR (match_key = ? AND status = 'PENDING') "
                + "ORDER BY CASE WHEN submission_key = ? THEN 0 ELSE 1 END LIMIT 1"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, submissionKey);
            statement.setString(2, matchKey);
            statement.setString(3, submissionKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? codec.read(result) : null;
            }
        }
    }

    private PunishmentApprovalRequest replayAfterConflict(PunishmentApprovalRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            return existingForSubmission(
                    connection,
                    request.submissionKey().value(),
                    request.proposal().matchKey().value(),
                    false
            );
        } catch (SQLException exception) {
            return null;
        }
    }

    private PunishmentApprovalRequest lock(Connection connection, UUID requestId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM punishment_requests WHERE request_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? codec.read(result) : null;
            }
        }
    }

    private void insertRequest(Connection connection, PunishmentApprovalRequest request) throws SQLException {
        PunishmentProposal proposal = request.proposal();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_requests(
                    request_id, submission_key, match_key, target_id, requester_id, requester_name,
                    requester_rank, reason_id, sanction_family, public_reason, internal_explanation,
                    configuration_version, visibility, required_rank, raw_ordinal, effective_ordinal,
                    selected_ordinal, recency_bonus, step_label, contribution_json, sanctions_json,
                    status, revision, created_at, updated_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(request.requestId()));
            statement.setString(2, request.submissionKey().value());
            statement.setString(3, proposal.matchKey().value());
            statement.setBytes(4, UuidBytes.toBytes(proposal.targetId()));
            statement.setBytes(5, UuidBytes.toBytes(proposal.requester().id()));
            statement.setString(6, proposal.requester().displayName());
            statement.setString(7, proposal.requester().rank().name());
            statement.setString(8, proposal.reasonId());
            statement.setString(9, proposal.family());
            statement.setString(10, proposal.publicReason());
            statement.setString(11, proposal.internalExplanation());
            statement.setString(12, proposal.configurationVersion());
            statement.setString(13, proposal.visibility().name());
            statement.setString(14, proposal.requiredRank().name());
            statement.setInt(15, proposal.escalation().rawOrdinal());
            statement.setInt(16, proposal.escalation().effectiveOrdinal());
            statement.setInt(17, proposal.escalation().selectedStep().ordinal());
            statement.setInt(18, proposal.escalation().recencyBonus());
            statement.setString(19, proposal.escalation().selectedStep().label());
            statement.setString(20, encodeContributions(proposal));
            statement.setString(21, encodeSanctions(proposal));
            statement.setString(22, request.status().name());
            statement.setLong(23, request.revision());
            statement.setTimestamp(24, Timestamp.from(request.createdAt()));
            statement.setTimestamp(25, Timestamp.from(request.createdAt()));
            statement.setTimestamp(26, Timestamp.from(request.expiresAt()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request was not inserted"
            );
        }
    }

    private void insertNotifications(Connection connection, PunishmentApprovalRequest request) throws SQLException {
        PunishmentProposal proposal = request.proposal();
        String payload = json(Map.of(
                "requestId", request.requestId().toString(),
                "requesterId", proposal.requester().id().toString(),
                "targetId", proposal.targetId().toString(),
                "reasonId", proposal.reasonId(),
                "requiredRank", approvalMinimum(proposal.requiredRank()).name(),
                "expiresAt", request.expiresAt().toString()
        ));
        try (PreparedStatement alert = connection.prepareStatement("""
                INSERT INTO staff_alerts(alert_id, recipient_id, minimum_rank, alert_type,
                    payload_json, created_at)
                VALUES (?, NULL, ?, 'PUNISHMENT_REQUEST_PENDING', ?, ?)
                """)) {
            alert.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            alert.setString(2, approvalMinimum(proposal.requiredRank()).name());
            alert.setString(3, payload);
            alert.setTimestamp(4, Timestamp.from(request.createdAt()));
            alert.executeUpdate();
        }
        try (PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'punishments', 'PUNISHMENT_REQUEST_PENDING', ?, ?, ?)
                """)) {
            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, "punishment-request:" + request.requestId() + ":pending");
            discord.setString(3, payload);
            discord.setTimestamp(4, Timestamp.from(request.createdAt()));
            discord.setTimestamp(5, Timestamp.from(request.createdAt()));
            discord.executeUpdate();
        }
    }

    private static StaffRank approvalMinimum(StaffRank requiredRank) {
        return requiredRank == StaffRank.ADMIN || requiredRank == StaffRank.FOUNDER
                ? requiredRank
                : StaffRank.MOD;
    }

    private static void ensureTarget(Connection connection, UUID targetId, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT IGNORE INTO players(player_id, first_seen_at, last_seen_at)
                VALUES (?, ?, ?)
                """)) {
            insert.setBytes(1, UuidBytes.toBytes(targetId));
            insert.setTimestamp(2, Timestamp.from(now));
            insert.setTimestamp(3, Timestamp.from(now));
            insert.executeUpdate();
        }
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT revision FROM players WHERE player_id = ? FOR UPDATE")) {
            lock.setBytes(1, UuidBytes.toBytes(targetId));
            try (ResultSet result = lock.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("punishment request target disappeared during submission");
                }
            }
        }
    }

    static int fulfillMatching(
            Connection connection,
            PunishmentPlan plan,
            CaseId caseId,
            Instant now,
            UUID excludedRequestId
    ) throws SQLException {
        PunishmentMatchKey matchKey = PunishmentMatchKey.of(plan.targetId(), plan.reasonId(), plan.sanctions());
        String selectSql = "SELECT request_id, revision FROM punishment_requests "
                + "WHERE status = 'PENDING' AND match_key = ? AND expires_at > ?"
                + (excludedRequestId == null ? "" : " AND request_id <> ?")
                + " FOR UPDATE";
        List<RequestRevision> matches = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(selectSql)) {
            select.setString(1, matchKey.value());
            select.setTimestamp(2, Timestamp.from(now));
            if (excludedRequestId != null) {
                select.setBytes(3, UuidBytes.toBytes(excludedRequestId));
            }
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    matches.add(new RequestRevision(
                            UuidBytes.fromBytes(result.getBytes("request_id")),
                            result.getLong("revision")
                    ));
                }
            }
        }
        for (RequestRevision match : matches) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE punishment_requests
                    SET status = 'FULFILLED_EXTERNALLY', revision = revision + 1,
                        resolved_by = ?, resolution_note = ?, resulting_case_id = ?,
                        resolved_at = ?, updated_at = ?
                    WHERE request_id = ? AND status = 'PENDING' AND revision = ?
                    """)) {
                update.setBytes(1, UuidBytes.toBytes(plan.actor().id()));
                update.setString(2, "Exact matching punishment was applied independently");
                update.setString(3, caseId.value());
                update.setTimestamp(4, Timestamp.from(now));
                update.setTimestamp(5, Timestamp.from(now));
                update.setBytes(6, UuidBytes.toBytes(match.requestId()));
                update.setLong(7, match.revision());
                JdbcTransactionSupport.requireSingleUpdate(
                        update.executeUpdate(),
                        "Matching punishment request changed during external fulfillment"
                );
            }
            insertEvent(
                    connection,
                    match.requestId(),
                    "FULFILLED_EXTERNALLY",
                    plan.actor().id(),
                    null,
                    caseId,
                    "Exact matching punishment was applied independently",
                    now
            );
            deleteLease(connection, match.requestId());
        }
        return matches.size();
    }

    private static void insertEvent(
            Connection connection,
            UUID requestId,
            String eventType,
            UUID actorId,
            Long fenceToken,
            CaseId caseId,
            String note,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_request_events(
                    event_id, request_id, event_type, actor_id, fence_token,
                    resulting_case_id, note, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(requestId));
            statement.setString(3, eventType);
            if (actorId == null) {
                statement.setNull(4, java.sql.Types.BINARY);
            } else {
                statement.setBytes(4, UuidBytes.toBytes(actorId));
            }
            if (fenceToken == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, fenceToken);
            }
            if (caseId == null) {
                statement.setNull(6, java.sql.Types.CHAR);
            } else {
                statement.setString(6, caseId.value());
            }
            statement.setString(7, note);
            statement.setTimestamp(8, Timestamp.from(occurredAt));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request event was not appended"
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

    private String encodeContributions(PunishmentProposal proposal) throws SQLException {
        try {
            return codec.encodeContributions(proposal.escalation().contributions());
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize punishment request contributions", exception);
        }
    }

    private String encodeSanctions(PunishmentProposal proposal) throws SQLException {
        try {
            return codec.encodeSanctions(proposal);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize punishment request sanctions", exception);
        }
    }

    private String json(Map<String, Object> payload) throws SQLException {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize punishment request notification", exception);
        }
    }

    private static IdempotencyKey approvalIdempotency(UUID requestId) {
        return new IdempotencyKey("punishment-request:" + requestId + ":approved");
    }

    private static String resourceKey(UUID requestId) {
        return "punishment-request:" + requestId;
    }

    private static void validateDecision(PunishmentApprovalLease lease, Actor approver, Instant now) {
        if (lease == null || approver == null || now == null) {
            throw new IllegalArgumentException("punishment request decision fields must be present");
        }
    }

    private static PunishmentRequestResult.Rejected rejected(String code, String message) {
        return new PunishmentRequestResult.Rejected(code, message);
    }

    private record RequestRevision(UUID requestId, long revision) {
    }
}
