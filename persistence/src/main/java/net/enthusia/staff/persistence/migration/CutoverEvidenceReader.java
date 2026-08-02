package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.CutoverEvidence;
import net.enthusia.staff.domain.migration.DecisionComparison;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.persistence.UuidBytes;

final class CutoverEvidenceReader {
    private final ObjectMapper json;

    CutoverEvidenceReader(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("cutover evidence mapper must be present");
        }
        this.json = json;
    }

    Optional<CutoverEvidenceBundle> read(
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
        ShadowRun latestRun = window.runs().getLast();
        ComparisonSummary latestShadow = readComparisons(
                connection,
                latestRun.runId(),
                latestRun.completedAt()
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
                authoritativeComparison.matched(ComparisonType.COUNTS),
                authoritativeComparison.matched(ComparisonType.CHECKSUMS),
                authoritativeComparison.matched(ComparisonType.ACTIVE_SANCTIONS),
                authoritativeComparison.matched(ComparisonType.UUID_MAPPINGS),
                authoritativeComparison.matched(ComparisonType.EXPIRATIONS),
                authoritativeComparison.decisions(ComparisonType.LOGIN_DECISIONS),
                authoritativeComparison.decisions(ComparisonType.MUTE_DECISIONS),
                authoritativeComparison.decisions(ComparisonType.IP_BAN_DECISIONS),
                unresolvedOperations(connection),
                migrationIdle(connection),
                state.mode() == OperationalMode.MAINTENANCE,
                finalRun != null
        );
        Optional<UUID> finalRunId = finalRun == null
                ? Optional.empty()
                : Optional.of(finalRun.runId());
        return Optional.of(new CutoverEvidenceBundle(evidence, finalRunId));
    }

    private ShadowWindow uninterruptedShadowWindow(Connection connection, Instant shadowEndedAt)
            throws SQLException {
        Timestamp lastFailure = lastShadowFailure(connection, shadowEndedAt);
        Timestamp lastMaintenanceAbort = lastMaintenanceAbort(connection, shadowEndedAt);
        Timestamp resetAt = later(lastFailure, lastMaintenanceAbort);
        String sql = resetAt == null ? """
                SELECT r.run_id, r.started_at, r.completed_at
                FROM migration_runs r
                WHERE r.mode = 'SHADOW' AND r.state = 'COMPLETED' AND r.mismatch_count = 0
                  AND r.completed_at <= ?
                  AND (SELECT COUNT(*) FROM shadow_comparisons s WHERE s.run_id = r.run_id) = ?
                  AND NOT EXISTS (
                       SELECT 1 FROM shadow_comparisons s
                       WHERE s.run_id = r.run_id AND s.matched = FALSE
                  )
                ORDER BY r.completed_at
                """ : """
                SELECT r.run_id, r.started_at, r.completed_at
                FROM migration_runs r
                WHERE r.mode = 'SHADOW' AND r.state = 'COMPLETED' AND r.mismatch_count = 0
                  AND r.started_at >= ? AND r.completed_at <= ?
                  AND (SELECT COUNT(*) FROM shadow_comparisons s WHERE s.run_id = r.run_id) = ?
                  AND NOT EXISTS (
                       SELECT 1 FROM shadow_comparisons s
                       WHERE s.run_id = r.run_id AND s.matched = FALSE
                  )
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
                    Timestamp startedAt = result.getTimestamp("started_at");
                    Timestamp completedAt = result.getTimestamp("completed_at");
                    if (startedAt == null || completedAt == null) {
                        throw new SQLException("completed shadow run is missing timestamps");
                    }
                    runs.add(new ShadowRun(
                            UuidBytes.fromBytes(result.getBytes("run_id")),
                            startedAt.toInstant(),
                            completedAt.toInstant()
                    ));
                }
                if (runs.isEmpty()) {
                    return null;
                }
                validateShadowRuns(connection, runs);
                return new ShadowWindow(runs.getFirst().startedAt(), runs);
            }
        }
    }

    private void validateShadowRuns(Connection connection, List<ShadowRun> runs) throws SQLException {
        for (ShadowRun run : runs) {
            if (readComparisons(connection, run.runId(), run.completedAt()) == null) {
                throw new SQLException("shadow run is missing a complete comparison summary");
            }
        }
    }

    private static Timestamp lastShadowFailure(Connection connection, Instant shadowEndedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(COALESCE(r.completed_at, r.started_at))
                FROM migration_runs r
                WHERE r.mode = 'SHADOW' AND r.started_at <= ?
                  AND (
                       r.state <> 'COMPLETED'
                       OR r.mismatch_count > 0
                       OR EXISTS (
                            SELECT 1 FROM shadow_comparisons s
                            WHERE s.run_id = r.run_id AND s.matched = FALSE
                       )
                  )
                """)) {
            statement.setTimestamp(1, Timestamp.from(shadowEndedAt));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("shadow failure boundary query returned no row");
                }
                return result.getTimestamp(1);
            }
        }
    }

    private static Timestamp lastMaintenanceAbort(Connection connection, Instant shadowEndedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT MAX(occurred_at)
                FROM audit_events
                WHERE event_type = 'LITEBANS_CUTOVER_MAINTENANCE_ABORTED' AND occurred_at <= ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(shadowEndedAt));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("maintenance-abort boundary query returned no row");
                }
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

    private FinalRun latestFinalRun(Connection connection, Instant maintenanceStartedAt)
            throws SQLException {
        UUID runId;
        Instant completedAt;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT run_id, state, completed_at
                FROM migration_runs
                WHERE mode = 'CUTOVER' AND started_at >= ?
                ORDER BY started_at DESC LIMIT 1
                """)) {
            statement.setTimestamp(1, Timestamp.from(maintenanceStartedAt));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Timestamp completed = result.getTimestamp("completed_at");
                if (!"COMPLETED".equals(result.getString("state")) || completed == null) {
                    return null;
                }
                runId = UuidBytes.fromBytes(result.getBytes("run_id"));
                completedAt = completed.toInstant();
            }
        }
        ComparisonSummary comparisons = readComparisons(connection, runId, completedAt);
        return comparisons == null ? null : new FinalRun(runId, comparisons);
    }

    private ComparisonSummary readComparisons(Connection connection, UUID runId, Instant completedAt)
            throws SQLException {
        Map<ComparisonType, Comparison> comparisons = new EnumMap<>(ComparisonType.class);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT comparison_type, matched, detail_json, compared_at
                FROM shadow_comparisons WHERE run_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ComparisonType type = ComparisonType.valueOf(result.getString("comparison_type"));
                    Timestamp comparedAt = result.getTimestamp("compared_at");
                    if (comparedAt == null || comparedAt.toInstant().isAfter(completedAt)) {
                        throw new SQLException("shadow comparison timestamp is inconsistent with its run");
                    }
                    JsonNode detail = parseDetail(result.getString("detail_json"));
                    long compared = requiredNonNegativeLong(detail, "compared");
                    long mismatched = requiredNonNegativeLong(detail, "mismatched");
                    boolean matched = result.getBoolean("matched");
                    if (result.wasNull()) {
                        throw new SQLException("persisted shadow comparison match flag is missing");
                    }
                    if (mismatched > compared || matched != (mismatched == 0)) {
                        throw new SQLException("inconsistent persisted shadow comparison detail");
                    }
                    Comparison previous = comparisons.put(
                            type,
                            new Comparison(matched, compared, mismatched)
                    );
                    if (previous != null) {
                        throw new SQLException("duplicate persisted shadow comparison type");
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new SQLException("unknown persisted shadow comparison type", exception);
            }
        }
        return comparisons.size() == ComparisonType.values().length
                ? new ComparisonSummary(comparisons)
                : null;
    }

    private JsonNode parseDetail(String detailJson) throws SQLException {
        if (detailJson == null || detailJson.isBlank()) {
            throw new SQLException("persisted shadow comparison detail is missing");
        }
        try {
            JsonNode detail = json.readTree(detailJson);
            if (detail == null || !detail.isObject()) {
                throw new SQLException("persisted shadow comparison detail must be an object");
            }
            return detail;
        } catch (JsonProcessingException exception) {
            throw new SQLException("invalid persisted shadow comparison detail", exception);
        }
    }

    private static long requiredNonNegativeLong(JsonNode detail, String field) throws SQLException {
        JsonNode value = detail.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0) {
            throw new SQLException("invalid persisted shadow comparison " + field);
        }
        return value.longValue();
    }

    private static boolean migrationIdle(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT NOT EXISTS(SELECT 1 FROM migration_runs WHERE state = 'RUNNING')");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("migration idle query returned no row");
            }
            return result.getBoolean(1);
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
            if (!result.next()) {
                throw new SQLException("unresolved operation query returned no row");
            }
            return result.getLong(1);
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

    private record ComparisonSummary(Map<ComparisonType, Comparison> comparisons) {
        private ComparisonSummary {
            comparisons = Map.copyOf(comparisons);
        }

        private boolean matched(ComparisonType type) {
            return comparisons.get(type).matched();
        }

        private DecisionComparison decisions(ComparisonType type) {
            Comparison comparison = comparisons.get(type);
            return new DecisionComparison(comparison.compared(), comparison.mismatched());
        }
    }

    private record FinalRun(UUID runId, ComparisonSummary comparisons) {
    }
}
