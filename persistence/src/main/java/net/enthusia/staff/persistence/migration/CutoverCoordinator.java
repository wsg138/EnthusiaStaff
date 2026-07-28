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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.UuidBytes;

public final class CutoverCoordinator {
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final Clock clock;
    private final CutoverGate gate = new CutoverGate();

    public CutoverCoordinator(DataSource dataSource, ObjectMapper json, Clock clock) {
        if (dataSource == null || json == null || clock == null) {
            throw new IllegalArgumentException("cutover coordinator dependencies must be present");
        }
        this.dataSource = dataSource;
        this.json = json;
        this.clock = clock;
    }

    public boolean enterMaintenance(UUID actorId, String reason) {
        validateTransition(actorId, reason);
        return transitionMode(
                OperationalMode.SHADOW_MIGRATION,
                OperationalMode.MAINTENANCE,
                actorId,
                reason,
                "LITEBANS_CUTOVER_MAINTENANCE_ENTERED"
        );
    }

    public boolean abortMaintenance(UUID actorId, String reason) {
        validateTransition(actorId, reason);
        return transitionMode(
                OperationalMode.MAINTENANCE,
                OperationalMode.SHADOW_MIGRATION,
                actorId,
                reason,
                "LITEBANS_CUTOVER_MAINTENANCE_ABORTED"
        );
    }

    public boolean freezeActiveAuthority(UUID actorId, String reason) {
        validateTransition(actorId, reason);
        return transitionMode(
                OperationalMode.ACTIVE,
                OperationalMode.READ_ONLY_FAILURE,
                actorId,
                reason,
                "LITEBANS_CUTOVER_EMERGENCY_FREEZE"
        );
    }

    public Optional<CutoverEvidence> latestEvidence() {
        try (Connection connection = dataSource.getConnection()) {
            OperationalStateSnapshot state = readOperationalState(connection, false);
            return assembleEvidence(connection, state, clock.instant()).map(EvidenceBundle::evidence);
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
        if (override.filter(value -> !value.actorId().equals(actorId)).isPresent()) {
            throw new IllegalArgumentException("Founder override actor must match the cutover actor");
        }
        UUID cutoverId = UUID.randomUUID();
        MigrationDatabaseLock migrationLock = MigrationDatabaseLock.acquire(dataSource);
        Throwable operationFailure = null;
        try {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                OperationalStateSnapshot current = readOperationalState(connection, true);
                if (current.mode() != OperationalMode.MAINTENANCE) {
                    connection.rollback();
                    return new CutoverOutcome(
                            new CutoverAssessment(false, false, List.of("MAINTENANCE_REQUIRED")),
                            false,
                            Optional.empty()
                    );
                }
                Optional<EvidenceBundle> assembled = assembleEvidence(connection, current, clock.instant());
                if (assembled.isEmpty()) {
                    connection.rollback();
                    return new CutoverOutcome(
                            new CutoverAssessment(false, false, List.of("NO_COMPLETED_SHADOW_EVIDENCE")),
                            false,
                            Optional.empty()
                    );
                }
                EvidenceBundle bundle = assembled.orElseThrow();
                CutoverEvidence evidence = bundle.evidence();
                CutoverAssessment assessment = gate.assess(evidence, override);
                if (!assessment.allowed()) {
                    connection.rollback();
                    return new CutoverOutcome(assessment, false, Optional.empty());
                }
                try (PreparedStatement cutover = connection.prepareStatement("""
                        INSERT INTO cutover_records(cutover_id, migration_run_id, assessment_json,
                            blockers_json, founder_override_used, authorized_by, authorized_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    cutover.setBytes(1, UuidBytes.toBytes(cutoverId));
                    if (bundle.finalRunId().isPresent()) {
                        cutover.setBytes(2, UuidBytes.toBytes(bundle.finalRunId().orElseThrow()));
                    } else {
                        cutover.setNull(2, java.sql.Types.BINARY);
                    }
                    cutover.setString(3, json.writeValueAsString(
                            assessmentJson(evidence, assessment, override)
                    ));
                    cutover.setString(4, json.writeValueAsString(assessment.blockers()));
                    cutover.setBoolean(5, assessment.founderOverrideUsed());
                    cutover.setBytes(6, UuidBytes.toBytes(actorId));
                    cutover.setTimestamp(7, Timestamp.from(clock.instant()));
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
                appendAudit(connection, cutoverId, actorId, assessment, override);
                connection.commit();
                return new CutoverOutcome(assessment, true, Optional.of(cutoverId));
                } catch (SQLException | JsonProcessingException exception) {
                    rollback(connection, exception);
                    throw new ModerationPersistenceException("Cutover activation transaction failed", exception);
                } catch (RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                } finally {
                    restoreAutoCommit(connection);
                }
            }
        } catch (SQLException exception) {
            ModerationPersistenceException failure =
                    new ModerationPersistenceException("Unable to open cutover transaction", exception);
            operationFailure = failure;
            throw failure;
        } catch (RuntimeException | Error exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            migrationLock.closeAfter(operationFailure);
        }
    }

    private Optional<EvidenceBundle> assembleEvidence(
            Connection connection,
            OperationalStateSnapshot state,
            Instant assessedAt
    ) throws SQLException {
        Instant shadowEndedAt = state.mode() == OperationalMode.MAINTENANCE
                ? state.updatedAt()
                : assessedAt;
        ShadowWindow window = uninterruptedShadowWindow(connection, shadowEndedAt);
        if (window == null) {
            return Optional.empty();
        }
        ComparisonSummary latestShadow = readComparisons(
                connection, window.runs().getLast().runId(), window.runs().getLast().completedAt()
        );
        if (latestShadow == null) {
            return Optional.empty();
        }
        FinalRun finalRun = state.mode() == OperationalMode.MAINTENANCE
                ? latestFinalRun(connection, state.updatedAt())
                : null;
        ComparisonSummary authoritativeComparison = finalRun == null
                ? latestShadow
                : finalRun.comparisons();
        CutoverEvidence evidence = new CutoverEvidence(
                window.startedAt(),
                shadowEndedAt,
                assessedAt,
                window.runs().stream().map(ShadowRun::completedAt).toList(),
                authoritativeComparison.matched("COUNTS"),
                authoritativeComparison.matched("CHECKSUMS"),
                authoritativeComparison.matched("ACTIVE_SANCTIONS"),
                authoritativeComparison.matched("UUID_MAPPINGS"),
                authoritativeComparison.matched("EXPIRATIONS"),
                authoritativeComparison.decisions("LOGIN_DECISIONS"),
                authoritativeComparison.decisions("MUTE_DECISIONS"),
                authoritativeComparison.decisions("IP_BAN_DECISIONS"),
                unresolvedOperations(connection),
                migrationIdle(connection),
                state.mode() == OperationalMode.MAINTENANCE,
                finalRun != null
        );
        return Optional.of(new EvidenceBundle(
                evidence,
                finalRun == null ? Optional.empty() : Optional.of(finalRun.runId())
        ));
    }

    private ShadowWindow uninterruptedShadowWindow(Connection connection, Instant shadowEndedAt) throws SQLException {
        Timestamp lastFailure;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(COALESCE(completed_at, started_at))
                FROM migration_runs
                WHERE mode = 'SHADOW' AND started_at <= ?
                  AND (state <> 'COMPLETED' OR mismatch_count > 0)
                """)) {
            statement.setTimestamp(1, Timestamp.from(shadowEndedAt));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                lastFailure = result.getTimestamp(1);
            }
        }
        Timestamp lastMaintenanceAbort = lastMaintenanceAbort(connection, shadowEndedAt);
        Timestamp resetAt = later(lastFailure, lastMaintenanceAbort);
        String sql = resetAt == null ? """
                SELECT r.run_id, r.started_at, r.completed_at
                FROM migration_runs r
                WHERE r.mode = 'SHADOW' AND r.state = 'COMPLETED' AND r.mismatch_count = 0
                  AND r.completed_at <= ?
                  AND (SELECT COUNT(DISTINCT s.comparison_type)
                       FROM shadow_comparisons s WHERE s.run_id = r.run_id) = ?
                ORDER BY r.completed_at
                """ : """
                SELECT r.run_id, r.started_at, r.completed_at
                FROM migration_runs r
                WHERE r.mode = 'SHADOW' AND r.state = 'COMPLETED' AND r.mismatch_count = 0
                  AND r.started_at >= ? AND r.completed_at <= ?
                  AND (SELECT COUNT(DISTINCT s.comparison_type)
                       FROM shadow_comparisons s WHERE s.run_id = r.run_id) = ?
                ORDER BY r.completed_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (resetAt != null) {
                statement.setTimestamp(index++, resetAt);
            }
            statement.setTimestamp(index++, Timestamp.from(shadowEndedAt));
            statement.setInt(index, ComparisonType.values().length);
            try (ResultSet result = statement.executeQuery()) {
                List<ShadowRun> runs = new ArrayList<>();
                while (result.next()) {
                    runs.add(new ShadowRun(
                            UuidBytes.fromBytes(result.getBytes("run_id")),
                            result.getTimestamp("started_at").toInstant(),
                            result.getTimestamp("completed_at").toInstant()
                    ));
                }
                if (runs.isEmpty()) {
                    return null;
                }
                return new ShadowWindow(runs.getFirst().startedAt(), runs);
            }
        }
    }

    private static Timestamp lastMaintenanceAbort(Connection connection, Instant shadowEndedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(occurred_at)
                FROM audit_events
                WHERE event_type = 'LITEBANS_CUTOVER_MAINTENANCE_ABORTED' AND occurred_at <= ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(shadowEndedAt));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getTimestamp(1);
            }
        }
    }

    private static Timestamp later(Timestamp first, Timestamp second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.after(second) ? first : second;
    }

    private FinalRun latestFinalRun(Connection connection, Instant maintenanceStarted) throws SQLException {
        UUID runId;
        Instant completedAt;
        String state;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT run_id, state, completed_at
                FROM migration_runs
                WHERE mode = 'CUTOVER' AND started_at >= ?
                ORDER BY started_at DESC LIMIT 1
                """)) {
            statement.setTimestamp(1, Timestamp.from(maintenanceStarted));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                state = result.getString("state");
                if (!"COMPLETED".equals(state) || result.getTimestamp("completed_at") == null) {
                    return null;
                }
                runId = UuidBytes.fromBytes(result.getBytes("run_id"));
                completedAt = result.getTimestamp("completed_at").toInstant();
            }
        }
        ComparisonSummary comparisons = readComparisons(connection, runId, completedAt);
        return comparisons == null ? null : new FinalRun(runId, comparisons);
    }

    private ComparisonSummary readComparisons(Connection connection, UUID runId, Instant completedAt)
            throws SQLException {
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
            } catch (IllegalArgumentException exception) {
                throw new SQLException("unknown persisted shadow comparison type", exception);
            }
        }
        return comparisons.size() == ComparisonType.values().length
                ? new ComparisonSummary(runId, completedAt, comparisons)
                : null;
    }

    private static boolean migrationIdle(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT NOT EXISTS(SELECT 1 FROM migration_runs WHERE state = 'RUNNING')");
             ResultSet result = statement.executeQuery()) {
            return result.next() && result.getBoolean(1);
        }
    }

    private static long unresolvedOperations(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    (SELECT COUNT(*) FROM recovery_quarantine WHERE resolved_at IS NULL)
                  + (SELECT COUNT(*) FROM network_outbox WHERE state = 'DEAD_LETTER')
                  + (SELECT COUNT(*) FROM discord_outbox WHERE state = 'DEAD_LETTER')
                  + (SELECT COUNT(*) FROM inventory_operations
                     WHERE state NOT IN ('COMMITTED', 'RESTORED', 'ROLLED_BACK'))
                  + (SELECT COUNT(*) FROM inventory_pending_patches WHERE state <> 'APPLIED')
                  + (SELECT COUNT(*) FROM economy_operations WHERE state <> 'UNLOCKED')
                  + (SELECT COUNT(*) FROM confiscated_asset_snapshots
                     WHERE restoration_state = 'RESERVED' AND restored_at IS NULL)
                """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static OperationalStateSnapshot readOperationalState(Connection connection, boolean forUpdate)
            throws SQLException {
        String sql = forUpdate ? """
                SELECT mode, revision, reason, updated_at
                FROM operational_state WHERE singleton_id = 1 FOR UPDATE
                """ : """
                SELECT mode, revision, reason, updated_at
                FROM operational_state WHERE singleton_id = 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
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

    private boolean transitionMode(
            OperationalMode expected,
            OperationalMode next,
            UUID actorId,
            String reason,
            String auditType
    ) {
        MigrationDatabaseLock migrationLock = MigrationDatabaseLock.acquire(dataSource);
        Throwable operationFailure = null;
        try {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                OperationalStateSnapshot current = readOperationalState(connection, true);
                if (current.mode() != expected) {
                    connection.rollback();
                    return false;
                }
                Instant now = clock.instant();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE operational_state
                        SET mode = ?, revision = revision + 1, reason = ?, updated_by = ?, updated_at = ?
                        WHERE singleton_id = 1 AND revision = ? AND mode = ?
                        """)) {
                    statement.setString(1, next.name());
                    statement.setString(2, reason.trim());
                    statement.setBytes(3, UuidBytes.toBytes(actorId));
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setLong(5, current.revision());
                    statement.setString(6, expected.name());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("operational mode changed during migration transition");
                    }
                }
                appendTransitionAudit(connection, actorId, expected, next, reason, auditType, now);
                connection.commit();
                return true;
                } catch (SQLException | JsonProcessingException exception) {
                    rollback(connection, exception);
                    throw new ModerationPersistenceException("Migration operational transition failed", exception);
                } catch (RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                } finally {
                    restoreAutoCommit(connection);
                }
            }
        } catch (SQLException exception) {
            ModerationPersistenceException failure = new ModerationPersistenceException(
                    "Unable to open migration transition transaction", exception
            );
            operationFailure = failure;
            throw failure;
        } catch (RuntimeException | Error exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            migrationLock.closeAfter(operationFailure);
        }
    }

    private void appendTransitionAudit(
            Connection connection,
            UUID actorId,
            OperationalMode previous,
            OperationalMode next,
            String reason,
            String auditType,
            Instant now
    ) throws SQLException, JsonProcessingException {
        UUID correlationId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, event_type,
                    outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(correlationId));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setString(4, auditType);
            statement.setString(5, json.writeValueAsString(Map.of(
                    "previousMode", previous.name(),
                    "nextMode", next.name(),
                    "reason", reason.trim()
            )));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void validateTransition(UUID actorId, String reason) {
        if (actorId == null || reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("migration transition actor and bounded reason are required");
        }
    }

    private static Map<String, Object> evidenceJson(CutoverEvidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("shadowStartedAt", evidence.shadowStartedAt().toString());
        value.put("shadowEndedAt", evidence.shadowEndedAt().toString());
        value.put("assessedAt", evidence.assessedAt().toString());
        value.put("successfulShadowSummaries", evidence.successfulShadowSummaries().stream()
                .map(Instant::toString).toList());
        value.put("countsMatch", evidence.countsMatch());
        value.put("checksumsMatch", evidence.checksumsMatch());
        value.put("activeSanctionsMatch", evidence.activeSanctionsMatch());
        value.put("uuidMappingsMatch", evidence.uuidMappingsMatch());
        value.put("expirationsMatch", evidence.expirationsMatch());
        value.put("loginDecisions", decisionJson(evidence.loginDecisions()));
        value.put("muteDecisions", decisionJson(evidence.muteDecisions()));
        value.put("ipBanDecisions", decisionJson(evidence.ipBanDecisions()));
        value.put("unresolvedOperations", evidence.unresolvedOperations());
        value.put("migrationIdle", evidence.migrationIdle());
        value.put("writesFrozen", evidence.writesFrozen());
        value.put("finalIncrementalImportComplete", evidence.finalIncrementalImportComplete());
        return Map.copyOf(value);
    }

    private static Map<String, Long> decisionJson(DecisionComparison comparison) {
        return Map.of(
                "compared", comparison.compared(),
                "mismatched", comparison.mismatched()
        );
    }

    private static Map<String, Object> assessmentJson(
            CutoverEvidence evidence,
            CutoverAssessment assessment,
            Optional<FounderOverride> override
    ) {
        Map<String, Object> value = new LinkedHashMap<>(evidenceJson(evidence));
        value.put("allowed", assessment.allowed());
        value.put("founderOverrideUsed", assessment.founderOverrideUsed());
        if (assessment.founderOverrideUsed()) {
            FounderOverride used = override.orElseThrow();
            value.put("founderOverride", Map.of(
                    "actorId", used.actorId().toString(),
                    "warningAcknowledgement", used.warningAcknowledgement(),
                    "reason", used.reason()
            ));
        }
        return Map.copyOf(value);
    }

    private void appendAudit(
            Connection connection,
            UUID cutoverId,
            UUID actorId,
            CutoverAssessment assessment,
            Optional<FounderOverride> override
    ) throws SQLException, JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("founderOverrideUsed", assessment.founderOverrideUsed());
        payload.put("blockers", assessment.blockers());
        if (assessment.founderOverrideUsed()) {
            FounderOverride used = override.orElseThrow();
            payload.put("founderOverrideWarningAcknowledgement", used.warningAcknowledgement());
            payload.put("founderOverrideReason", used.reason());
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, event_type,
                    outcome, event_json, occurred_at)
                VALUES (?, ?, ?, 'LITEBANS_CUTOVER_ACTIVATED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(cutoverId));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setString(4, json.writeValueAsString(payload));
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

    private record ShadowRun(UUID runId, Instant startedAt, Instant completedAt) {
    }

    private record ShadowWindow(Instant startedAt, List<ShadowRun> runs) {
        private ShadowWindow {
            runs = List.copyOf(runs);
        }
    }

    private record ComparisonSummary(
            UUID runId,
            Instant completedAt,
            Map<ComparisonType, Comparison> comparisons
    ) {
        private ComparisonSummary {
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

    private record FinalRun(UUID runId, ComparisonSummary comparisons) {
    }

    private record EvidenceBundle(CutoverEvidence evidence, Optional<UUID> finalRunId) {
    }
}
