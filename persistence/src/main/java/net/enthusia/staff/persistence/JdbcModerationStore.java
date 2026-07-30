package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public final class JdbcModerationStore implements ModerationStore {
    private static final int PROTOCOL_VERSION = 1;

    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcModerationStore(DataSource dataSource, ObjectMapper json) {
        if (dataSource == null || json == null) {
            throw new IllegalArgumentException("dataSource and json must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
    }

    @Override
    public List<PriorOffense> relatedHistory(UUID targetId, String family) {
        String sql = """
                SELECT c.sanction_family, ps.effective_ordinal, c.issued_at,
                       COALESCE(MAX(s.ended_at), MAX(s.expiration_at), c.issued_at) ended_at,
                       ps.escalation_contributes, c.state,
                       COALESCE(MAX(CASE WHEN s.sanction_type IN (
                                                    'BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN'
                                                ) THEN 80
                                         WHEN s.sanction_type = 'MUTE' THEN 50
                                         ELSE 20 END), 0) severity
                FROM cases c
                JOIN punishment_steps ps ON ps.case_id = c.case_id
                LEFT JOIN sanctions s ON s.case_id = c.case_id
                WHERE c.target_id = ? AND c.sanction_family = ?
                GROUP BY c.case_id, c.sanction_family, ps.effective_ordinal, c.issued_at,
                         ps.escalation_contributes, c.state
                ORDER BY c.issued_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setString(2, family);
            try (ResultSet results = statement.executeQuery()) {
                List<PriorOffense> history = new ArrayList<>();
                while (results.next()) {
                    history.add(new PriorOffense(
                            results.getString("sanction_family"),
                            results.getInt("severity"),
                            results.getInt("effective_ordinal"),
                            results.getTimestamp("ended_at").toInstant(),
                            results.getBoolean("escalation_contributes"),
                            "FULLY_OVERTURNED".equals(results.getString("state"))
                    ));
                }
                return List.copyOf(history);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read related punishment history", exception);
        }
    }

    @Override
    public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                PunishmentResult.Accepted accepted = createPunishment(connection, plan);
                JdbcPunishmentRequestStore.fulfillMatching(
                        connection,
                        plan,
                        accepted.caseId(),
                        plan.issuedAt(),
                        null
                );
                connection.commit();
                return accepted;
            } catch (SQLException exception) {
                rollback(connection, exception);
                CaseId replay = existingCaseAfterConflict(plan.idempotencyKey().value());
                if (replay != null) {
                    return new PunishmentResult.Accepted(replay, true);
                }
                throw new ModerationPersistenceException("Punishment transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open punishment transaction", exception);
        }
    }

    PunishmentResult.Accepted createPunishment(Connection connection, PunishmentPlan plan) throws SQLException {
        CaseId replay = existingCase(connection, plan.idempotencyKey().value());
        if (replay != null) {
            return new PunishmentResult.Accepted(replay, true);
        }
        ensureTargetAndLock(connection, plan.targetId(), plan.issuedAt());
        insertCase(connection, plan);
        try {
            insertStep(connection, plan);
            List<UUID> sanctionIds = insertSanctions(connection, plan);
            insertAudit(connection, plan, sanctionIds);
            insertOutboxes(connection, plan, sanctionIds);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize punishment transaction payload", exception);
        }
        return new PunishmentResult.Accepted(plan.caseId(), false);
    }

    private static void ensureTargetAndLock(Connection connection, UUID targetId, Instant now) throws SQLException {
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
            try (ResultSet results = lock.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("target row disappeared during transaction");
                }
            }
        }
    }

    private static void insertCase(Connection connection, PunishmentPlan plan) throws SQLException {
        String sql = """
                INSERT INTO cases(case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                    public_reason, exact_reason_id, sanction_family, internal_explanation,
                    configuration_version, visibility, issued_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.caseId().value());
            statement.setString(2, plan.idempotencyKey().value());
            statement.setBytes(3, UuidBytes.toBytes(plan.targetId()));
            statement.setBytes(4, UuidBytes.toBytes(plan.actor().id()));
            statement.setString(5, plan.actor().displayName());
            statement.setString(6, plan.actor().rank().name());
            statement.setString(7, plan.publicReason());
            statement.setString(8, plan.reasonId());
            statement.setString(9, plan.family());
            statement.setString(10, plan.internalExplanation());
            statement.setString(11, plan.configurationVersion());
            statement.setString(12, plan.visibility().name());
            statement.setTimestamp(13, Timestamp.from(plan.issuedAt()));
            statement.executeUpdate();
        }
    }

    private void insertStep(Connection connection, PunishmentPlan plan)
            throws SQLException, JsonProcessingException {
        String sql = """
                INSERT INTO punishment_steps(case_id, raw_ordinal, effective_ordinal, recency_bonus,
                    step_label, contribution_json, escalation_contributes)
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.caseId().value());
            statement.setInt(2, plan.escalation().rawOrdinal());
            statement.setInt(3, plan.escalation().effectiveOrdinal());
            statement.setInt(4, plan.escalation().recencyBonus());
            statement.setString(5, plan.escalation().selectedStep().label());
            statement.setString(6, json.writeValueAsString(plan.escalation().contributions()));
            statement.executeUpdate();
        }
    }

    private List<UUID> insertSanctions(Connection connection, PunishmentPlan plan)
            throws SQLException, JsonProcessingException {
        String sanctionSql = """
                INSERT INTO sanctions(sanction_id, case_id, target_id, sanction_type, status,
                    issued_at, activated_at, expiration_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String eventSql = """
                INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id,
                    occurred_at, event_json)
                VALUES (?, ?, 'CREATED', ?, ?, ?)
                """;
        String warningSql = """
                INSERT INTO warnings(warning_id, case_id, sanction_id) VALUES (?, ?, ?)
                """;
        List<UUID> identifiers = new ArrayList<>();
        try (PreparedStatement sanction = connection.prepareStatement(sanctionSql);
             PreparedStatement event = connection.prepareStatement(eventSql);
             PreparedStatement warning = connection.prepareStatement(warningSql)) {
            for (SanctionSpec spec : plan.sanctions()) {
                UUID sanctionId = UUID.randomUUID();
                identifiers.add(sanctionId);
                sanction.setBytes(1, UuidBytes.toBytes(sanctionId));
                sanction.setString(2, plan.caseId().value());
                sanction.setBytes(3, UuidBytes.toBytes(plan.targetId()));
                sanction.setString(4, spec.type().name());
                sanction.setString(5, initialStatus(spec));
                sanction.setTimestamp(6, Timestamp.from(plan.issuedAt()));
                sanction.setTimestamp(7, Timestamp.from(plan.issuedAt()));
                if (spec.length().isPermanent()) {
                    sanction.setNull(8, Types.TIMESTAMP);
                } else if (spec.length().isInstant()) {
                    sanction.setTimestamp(8, Timestamp.from(plan.issuedAt()));
                } else {
                    sanction.setTimestamp(8, Timestamp.from(
                            spec.length().expirationFrom(plan.issuedAt()).orElseThrow()
                    ));
                }
                sanction.addBatch();

                event.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                event.setBytes(2, UuidBytes.toBytes(sanctionId));
                event.setBytes(3, UuidBytes.toBytes(plan.actor().id()));
                event.setTimestamp(4, Timestamp.from(plan.issuedAt()));
                event.setString(5, json.writeValueAsString(Map.of("type", spec.type().name())));
                event.addBatch();

                if (spec.type() == net.enthusia.staff.domain.sanction.SanctionType.WARNING) {
                    warning.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                    warning.setString(2, plan.caseId().value());
                    warning.setBytes(3, UuidBytes.toBytes(sanctionId));
                    warning.addBatch();
                }
            }
            sanction.executeBatch();
            event.executeBatch();
            warning.executeBatch();
        }
        return List.copyOf(identifiers);
    }

    private static String initialStatus(SanctionSpec specification) {
        if (!specification.length().isInstant()) {
            return "ACTIVE";
        }
        return specification.type() == net.enthusia.staff.domain.sanction.SanctionType.WARNING
                ? "APPLIED"
                : "PENDING";
    }

    private void insertAudit(Connection connection, PunishmentPlan plan, List<UUID> sanctionIds)
            throws SQLException, JsonProcessingException {
        String sql = """
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'PUNISHMENT_CREATED', 'COMMITTED', ?, ?)
                """;
        UUID correlationId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(correlationId));
            statement.setBytes(3, UuidBytes.toBytes(plan.actor().id()));
            statement.setBytes(4, UuidBytes.toBytes(plan.targetId()));
            statement.setString(5, plan.caseId().value());
            statement.setString(6, json.writeValueAsString(Map.of(
                    "reasonId", plan.reasonId(),
                    "sanctionIds", sanctionIds,
                    "configurationVersion", plan.configurationVersion()
            )));
            statement.setTimestamp(7, Timestamp.from(plan.issuedAt()));
            statement.executeUpdate();
        }
    }

    private void insertOutboxes(Connection connection, PunishmentPlan plan, List<UUID> sanctionIds)
            throws SQLException, JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", plan.caseId().value());
        payload.put("targetId", plan.targetId().toString());
        payload.put("reasonId", plan.reasonId());
        payload.put("sanctionIds", sanctionIds.stream().map(UUID::toString).toList());
        String serialized = json.writeValueAsString(payload);
        try (PreparedStatement network = connection.prepareStatement("""
                INSERT INTO network_outbox(message_id, idempotency_key, destination, message_type,
                    protocol_version, payload_json, available_at, created_at)
                VALUES (?, ?, 'broadcast', 'PUNISHMENT_CREATED', ?, ?, ?, ?)
                """)) {
            network.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            network.setString(2, "case:" + plan.caseId().value() + ":network-created");
            network.setInt(3, PROTOCOL_VERSION);
            network.setString(4, serialized);
            network.setTimestamp(5, Timestamp.from(plan.issuedAt()));
            network.setTimestamp(6, Timestamp.from(plan.issuedAt()));
            network.executeUpdate();
        }
        try (PreparedStatement discord = connection.prepareStatement("""
                INSERT INTO discord_outbox(message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at)
                VALUES (?, ?, 'punishments', 'PUNISHMENT_CREATED', ?, ?, ?)
                """)) {
            discord.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            discord.setString(2, "case:" + plan.caseId().value() + ":discord-created");
            discord.setString(3, serialized);
            discord.setTimestamp(4, Timestamp.from(plan.issuedAt()));
            discord.setTimestamp(5, Timestamp.from(plan.issuedAt()));
            discord.executeUpdate();
        }
        if (plan.actor().rank() == net.enthusia.staff.domain.auth.StaffRank.SYSTEM) {
            try (PreparedStatement alert = connection.prepareStatement("""
                    INSERT INTO staff_alerts(alert_id, recipient_id, minimum_rank, alert_type,
                        payload_json, created_at)
                    VALUES (?, NULL, 'HELPER', 'AUTOMATIC_PUNISHMENT_CREATED', ?, ?)
                    """)) {
                alert.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                alert.setString(2, serialized);
                alert.setTimestamp(3, Timestamp.from(plan.issuedAt()));
                alert.executeUpdate();
            }
        }
    }

    private static CaseId existingCase(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT case_id FROM cases WHERE idempotency_key = ?")) {
            statement.setString(1, idempotencyKey);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? new CaseId(results.getString(1)) : null;
            }
        }
    }

    private CaseId existingCaseAfterConflict(String idempotencyKey) {
        try (Connection connection = dataSource.getConnection()) {
            return existingCase(connection, idempotencyKey);
        } catch (SQLException exception) {
            return null;
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
            // Closing the connection evicts or resets it; the original failure remains authoritative.
        }
    }
}
