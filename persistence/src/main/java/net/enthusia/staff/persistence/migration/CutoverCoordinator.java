package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.CutoverAssessment;
import net.enthusia.staff.domain.migration.CutoverEvidence;
import net.enthusia.staff.domain.migration.CutoverGate;
import net.enthusia.staff.domain.migration.DecisionComparison;
import net.enthusia.staff.domain.migration.FounderOverride;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.persistence.JdbcOperationalStateStore;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.UuidBytes;

public final class CutoverCoordinator {
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final Clock clock;
    private final JdbcOperationalStateStore states;
    private final CutoverGate gate = new CutoverGate();

    public CutoverCoordinator(DataSource dataSource, ObjectMapper json, Clock clock) {
        if (dataSource == null || json == null || clock == null) {
            throw new IllegalArgumentException("cutover coordinator dependencies must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
        this.clock = clock;
        this.states = new JdbcOperationalStateStore(dataSource);
    }

    public boolean enterMaintenance(UUID actorId, String reason) {
        OperationalStateSnapshot current = states.current();
        if (current.mode() != OperationalMode.SHADOW_MIGRATION) {
            return false;
        }
        return states.transition(
                current.revision(), OperationalMode.MAINTENANCE, actorId, reason, clock.instant()
        );
    }

    public Optional<CutoverEvidence> latestEvidence() {
        try (Connection connection = dataSource.getConnection()) {
            Instant shadowStarted = uninterruptedShadowStart(connection);
            LatestShadow latest = latestShadow(connection);
            if (shadowStarted == null || latest == null) {
                return Optional.empty();
            }
            OperationalStateSnapshot state = states.current();
            boolean writesFrozen = state.mode() == OperationalMode.MAINTENANCE;
            boolean finalImport = writesFrozen && finalImportCompletedAfter(connection, state.updatedAt());
            long unresolved = unresolvedOperations(connection);
            return Optional.of(new CutoverEvidence(
                    shadowStarted,
                    clock.instant(),
                    latest.matched("COUNTS"),
                    latest.matched("CHECKSUMS"),
                    latest.matched("ACTIVE_SANCTIONS"),
                    latest.matched("UUID_MAPPINGS"),
                    latest.matched("EXPIRATIONS"),
                    latest.decisions("LOGIN_DECISIONS"),
                    latest.decisions("MUTE_DECISIONS"),
                    latest.decisions("IP_BAN_DECISIONS"),
                    unresolved,
                    writesFrozen,
                    finalImport
            ));
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to assemble cutover evidence", exception);
        }
    }

    public CutoverAssessment assess(Optional<FounderOverride> override) {
        return latestEvidence()
                .map(evidence -> gate.assess(evidence, override))
                .orElseGet(() -> new CutoverAssessment(false, false, List.of("NO_COMPLETED_SHADOW_EVIDENCE")));
    }

    public CutoverOutcome activate(UUID actorId, Optional<FounderOverride> override) {
        if (actorId == null || override == null) {
            throw new IllegalArgumentException("cutover actor and override container are required");
        }
        CutoverEvidence evidence = latestEvidence().orElse(null);
        if (evidence == null) {
            return new CutoverOutcome(
                    new CutoverAssessment(false, false, List.of("NO_COMPLETED_SHADOW_EVIDENCE")),
                    false,
                    Optional.empty()
            );
        }
        CutoverAssessment assessment = gate.assess(evidence, override);
        if (!assessment.allowed()) {
            return new CutoverOutcome(assessment, false, Optional.empty());
        }
        UUID cutoverId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                OperationalStateSnapshot current = lockOperationalState(connection);
                if (current.mode() != OperationalMode.MAINTENANCE) {
                    connection.rollback();
                    return new CutoverOutcome(
                            new CutoverAssessment(false, false, List.of("MAINTENANCE_REQUIRED")),
                            false,
                            Optional.empty()
                    );
                }
                try (PreparedStatement cutover = connection.prepareStatement("""
                        INSERT INTO cutover_records(cutover_id, assessment_json, blockers_json,
                            founder_override_used, authorized_by, authorized_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    cutover.setBytes(1, UuidBytes.toBytes(cutoverId));
                    cutover.setString(2, json.writeValueAsString(evidence));
                    cutover.setString(3, json.writeValueAsString(assessment.blockers()));
                    cutover.setBoolean(4, assessment.founderOverrideUsed());
                    cutover.setBytes(5, UuidBytes.toBytes(actorId));
                    cutover.setTimestamp(6, Timestamp.from(clock.instant()));
                    cutover.executeUpdate();
                }
                try (PreparedStatement transition = connection.prepareStatement("""
                        UPDATE operational_state
                        SET mode = 'ACTIVE', revision = revision + 1,
                            reason = 'Validated LiteBans cutover', updated_by = ?, updated_at = ?
                        WHERE singleton_id = 1 AND revision = ? AND mode = 'MAINTENANCE'
                        """)) {
                    transition.setBytes(1, UuidBytes.toBytes(actorId));
                    transition.setTimestamp(2, Timestamp.from(clock.instant()));
                    transition.setLong(3, current.revision());
                    if (transition.executeUpdate() != 1) {
                        throw new SQLException("operational state changed during cutover");
                    }
                }
                appendAudit(connection, cutoverId, actorId, assessment);
                connection.commit();
                return new CutoverOutcome(assessment, true, Optional.of(cutoverId));
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                throw new ModerationPersistenceException("Cutover activation transaction failed", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open cutover transaction", exception);
        }
    }

    private Instant uninterruptedShadowStart(Connection connection) throws SQLException {
        Timestamp lastFailure;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(COALESCE(completed_at, started_at))
                FROM migration_runs
                WHERE mode = 'SHADOW' AND (state <> 'COMPLETED' OR mismatch_count > 0)
                """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            lastFailure = result.getTimestamp(1);
        }
        String sql = lastFailure == null ? """
                SELECT MIN(started_at) FROM migration_runs
                WHERE mode = 'SHADOW' AND state = 'COMPLETED' AND mismatch_count = 0
                """ : """
                SELECT MIN(started_at) FROM migration_runs
                WHERE mode = 'SHADOW' AND state = 'COMPLETED' AND mismatch_count = 0 AND started_at > ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (lastFailure != null) {
                statement.setTimestamp(1, lastFailure);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getTimestamp(1) != null ? result.getTimestamp(1).toInstant() : null;
            }
        }
    }

    private LatestShadow latestShadow(Connection connection) throws SQLException {
        UUID runId;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT run_id FROM migration_runs
                WHERE mode = 'SHADOW' AND state = 'COMPLETED'
                ORDER BY completed_at DESC LIMIT 1
                """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return null;
            }
            runId = UuidBytes.fromBytes(result.getBytes(1));
        }
        Map<ComparisonType, Comparison> comparisons = new EnumMap<>(ComparisonType.class);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT comparison_type, matched, detail_json
                FROM shadow_comparisons WHERE run_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ComparisonType type = ComparisonType.valueOf(result.getString("comparison_type"));
                    JsonNode detail;
                    try {
                        detail = json.readTree(result.getString("detail_json"));
                    } catch (JsonProcessingException exception) {
                        throw new SQLException("invalid persisted comparison detail", exception);
                    }
                    comparisons.put(type, new Comparison(
                            result.getBoolean("matched"),
                            detail.path("compared").asLong(),
                            detail.path("mismatched").asLong()
                    ));
                }
            }
        }
        return comparisons.size() == ComparisonType.values().length ? new LatestShadow(comparisons) : null;
    }

    private static boolean finalImportCompletedAfter(Connection connection, Instant maintenanceStarted)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS(
                    SELECT 1 FROM migration_runs
                    WHERE mode = 'IMPORT' AND state = 'COMPLETED' AND completed_at >= ?
                )
                """)) {
            statement.setTimestamp(1, Timestamp.from(maintenanceStarted));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static long unresolvedOperations(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    (SELECT COUNT(*) FROM recovery_quarantine WHERE resolved_at IS NULL)
                  + (SELECT COUNT(*) FROM network_outbox WHERE state = 'DEAD_LETTER')
                  + (SELECT COUNT(*) FROM discord_outbox WHERE state = 'DEAD_LETTER')
                  + (SELECT COUNT(*) FROM inventory_pending_patches WHERE state IN ('CONFLICT', 'QUARANTINED'))
                """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static OperationalStateSnapshot lockOperationalState(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT mode, revision, reason, updated_at
                FROM operational_state WHERE singleton_id = 1 FOR UPDATE
                """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("operational state singleton missing");
            }
            return new OperationalStateSnapshot(
                    OperationalMode.valueOf(result.getString("mode")),
                    result.getLong("revision"),
                    result.getString("reason"),
                    result.getTimestamp("updated_at").toInstant()
            );
        }
    }

    private void appendAudit(
            Connection connection,
            UUID cutoverId,
            UUID actorId,
            CutoverAssessment assessment
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, event_type,
                    outcome, event_json, occurred_at)
                VALUES (?, ?, ?, 'LITEBANS_CUTOVER_ACTIVATED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(cutoverId));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setString(4, json.writeValueAsString(Map.of(
                    "founderOverrideUsed", assessment.founderOverrideUsed(),
                    "blockers", assessment.blockers()
            )));
            statement.setTimestamp(5, Timestamp.from(clock.instant()));
            statement.executeUpdate();
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

    private enum ComparisonType {
        COUNTS,
        CHECKSUMS,
        ACTIVE_SANCTIONS,
        UUID_MAPPINGS,
        EXPIRATIONS,
        LOGIN_DECISIONS,
        MUTE_DECISIONS,
        IP_BAN_DECISIONS
    }

    private record Comparison(boolean matched, long compared, long mismatched) {
    }

    private record LatestShadow(Map<ComparisonType, Comparison> comparisons) {
        private LatestShadow {
            comparisons = Map.copyOf(comparisons);
        }

        private boolean matched(String type) {
            return comparisons.get(ComparisonType.valueOf(type)).matched();
        }

        private DecisionComparison decisions(String type) {
            Comparison comparison = comparisons.get(ComparisonType.valueOf(type));
            return new DecisionComparison(comparison.compared(), comparison.mismatched());
        }
    }
}
