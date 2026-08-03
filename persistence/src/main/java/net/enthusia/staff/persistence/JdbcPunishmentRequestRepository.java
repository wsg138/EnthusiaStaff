package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentMatchKey;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.escalation.DecayEligibility;

final class JdbcPunishmentRequestRepository {
    private static final String COLUMNS = """
            request_id, submission_key, match_key, target_id, requester_id, requester_name,
            requester_rank, reason_id, sanction_family, public_reason, internal_explanation,
            configuration_version, visibility, required_rank, raw_ordinal, effective_ordinal,
            selected_ordinal, recency_bonus, step_label, contribution_json, sanctions_json,
            decay_eligible, status, revision, resolved_by, resolution_note, resulting_case_id,
            created_at, updated_at, expires_at, resolved_at
            """;
    private static final String SELECT_REQUESTS = "SELECT " + COLUMNS + " FROM punishment_requests ";

    private final DataSource dataSource;
    private final JdbcPunishmentRequestCodec codec;

    JdbcPunishmentRequestRepository(DataSource dataSource, JdbcPunishmentRequestCodec codec) {
        if (dataSource == null || codec == null) {
            throw new IllegalArgumentException("data source and punishment request codec must be present");
        }
        this.dataSource = dataSource;
        this.codec = codec;
    }

    Optional<PunishmentApprovalRequest> find(UUID requestId) {
        String sql = SELECT_REQUESTS + "WHERE request_id = ?";
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

    List<PunishmentApprovalRequest> pending(Instant now, int limit) {
        String sql = SELECT_REQUESTS
                + "WHERE status = 'PENDING' AND expires_at > ? ORDER BY created_at LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PunishmentApprovalRequest> values = new ArrayList<>();
                while (result.next()) {
                    values.add(codec.read(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to list pending punishment requests", exception);
        }
    }

    PunishmentApprovalRequest existingForSubmission(
            Connection connection,
            String submissionKey,
            PunishmentMatchKey matchKey,
            boolean lock
    ) throws SQLException {
        String sql = SELECT_REQUESTS
                + "WHERE submission_key = ? OR open_match_key = ? "
                + "ORDER BY CASE WHEN submission_key = ? THEN 0 ELSE 1 END LIMIT 1"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, submissionKey);
            statement.setString(2, matchKey.value());
            statement.setString(3, submissionKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? codec.read(result) : null;
            }
        }
    }

    PunishmentApprovalRequest lock(Connection connection, UUID requestId) throws SQLException {
        String sql = SELECT_REQUESTS + "WHERE request_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? codec.read(result) : null;
            }
        }
    }

    void lockTarget(Connection connection, UUID targetId, Instant now) throws SQLException {
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
                    throw new SQLException("punishment request target disappeared during transaction");
                }
            }
        }
    }

    void insert(Connection connection, PunishmentApprovalRequest request) throws SQLException {
        PunishmentProposal proposal = request.proposal();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_requests(
                    request_id, submission_key, match_key, open_match_key,
                    target_id, requester_id, requester_name, requester_rank,
                    reason_id, sanction_family, public_reason, internal_explanation,
                    configuration_version, visibility, required_rank, raw_ordinal,
                    effective_ordinal, selected_ordinal, recency_bonus, step_label,
                    contribution_json, sanctions_json, decay_eligible, status, revision,
                    created_at, updated_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int index = 1;
            statement.setBytes(index++, UuidBytes.toBytes(request.requestId()));
            statement.setString(index++, request.submissionKey().value());
            statement.setString(index++, proposal.matchKey().value());
            statement.setString(index++, proposal.matchKey().value());
            statement.setBytes(index++, UuidBytes.toBytes(proposal.targetId()));
            statement.setBytes(index++, UuidBytes.toBytes(proposal.requester().id()));
            statement.setString(index++, proposal.requester().displayName());
            statement.setString(index++, proposal.requester().rank().name());
            statement.setString(index++, proposal.reasonId());
            statement.setString(index++, proposal.family());
            statement.setString(index++, proposal.publicReason());
            statement.setString(index++, proposal.internalExplanation());
            statement.setString(index++, proposal.configurationVersion());
            statement.setString(index++, proposal.visibility().name());
            statement.setString(index++, proposal.requiredRank().name());
            statement.setInt(index++, proposal.escalation().rawOrdinal());
            statement.setInt(index++, proposal.escalation().effectiveOrdinal());
            statement.setInt(index++, proposal.escalation().selectedStep().ordinal());
            statement.setInt(index++, proposal.escalation().recencyBonus());
            statement.setString(index++, proposal.escalation().selectedStep().label());
            statement.setString(index++, encodeContributions(proposal));
            statement.setString(index++, encodeSanctions(proposal));
            writeDecayEligibility(
                    statement,
                    index,
                    proposal.escalation().resultingOffenseDecayEligibility()
            );
            index++;
            statement.setString(index++, request.status().name());
            statement.setLong(index++, request.revision());
            statement.setTimestamp(index++, Timestamp.from(request.createdAt()));
            statement.setTimestamp(index++, Timestamp.from(request.createdAt()));
            statement.setTimestamp(index, Timestamp.from(request.expiresAt()));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request was not inserted"
            );
        }
    }

    PunishmentApprovalRequest resolve(
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
                SET status = ?, open_match_key = NULL, revision = revision + 1,
                    resolved_by = ?, resolution_note = ?, resulting_case_id = ?,
                    resolved_at = ?, updated_at = ?
                WHERE request_id = ? AND status = 'PENDING' AND revision = ?
                """)) {
            statement.setString(1, status.name());
            setUuid(statement, 2, resolvedBy);
            statement.setString(3, note);
            if (caseId == null) {
                statement.setNull(4, Types.CHAR);
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

    List<PunishmentApprovalRequest> expired(Connection connection, Instant now, int limit)
            throws SQLException {
        String sql = SELECT_REQUESTS
                + "WHERE status = 'PENDING' AND expires_at <= ? "
                + "ORDER BY expires_at, request_id LIMIT ? FOR UPDATE SKIP LOCKED";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<PunishmentApprovalRequest> values = new ArrayList<>();
                while (result.next()) {
                    values.add(codec.read(result));
                }
                return List.copyOf(values);
            }
        }
    }

    List<PunishmentApprovalRequest> matchingPending(
            Connection connection,
            PunishmentMatchKey matchKey,
            Instant now,
            UUID excludedRequestId
    ) throws SQLException {
        String sql = excludedRequestId == null
                ? SELECT_REQUESTS + "WHERE status = 'PENDING' AND open_match_key = ? "
                    + "AND expires_at > ? ORDER BY request_id FOR UPDATE"
                : SELECT_REQUESTS + "WHERE status = 'PENDING' AND open_match_key = ? "
                    + "AND expires_at > ? AND request_id <> ? ORDER BY request_id FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, matchKey.value());
            statement.setTimestamp(2, Timestamp.from(now));
            if (excludedRequestId != null) {
                statement.setBytes(3, UuidBytes.toBytes(excludedRequestId));
            }
            try (ResultSet result = statement.executeQuery()) {
                List<PunishmentApprovalRequest> values = new ArrayList<>();
                while (result.next()) {
                    values.add(codec.read(result));
                }
                return List.copyOf(values);
            }
        }
    }

    PunishmentApprovalRequest replayAfterConflict(PunishmentApprovalRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            return existingForSubmission(
                    connection,
                    request.submissionKey().value(),
                    request.proposal().matchKey(),
                    false
            );
        } catch (SQLException exception) {
            return null;
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

    private static void writeDecayEligibility(
            PreparedStatement statement,
            int index,
            DecayEligibility eligibility
    ) throws SQLException {
        if (eligibility == DecayEligibility.UNKNOWN) {
            statement.setNull(index, Types.BOOLEAN);
            return;
        }
        statement.setBoolean(index, eligibility == DecayEligibility.ELIGIBLE);
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(value));
        }
    }
}
