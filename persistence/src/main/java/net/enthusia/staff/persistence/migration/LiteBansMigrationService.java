package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.migration.LegacySanction;
import net.enthusia.staff.domain.migration.LegacySanctionType;
import net.enthusia.staff.domain.migration.DecisionComparison;
import net.enthusia.staff.domain.migration.MigrationChecksum;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.JdbcPlayerDirectory;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.UuidBytes;

public final class LiteBansMigrationService {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 1L);

    private final DataSource target;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureIdentifiers identifiers;
    private final JdbcPlayerDirectory players;

    public LiteBansMigrationService(DataSource target, ObjectMapper json, Clock clock) {
        if (target == null || json == null || clock == null) {
            throw new IllegalArgumentException("migration dependencies must be present");
        }
        this.target = target;
        this.json = json;
        this.clock = clock;
        this.identifiers = new SecureIdentifiers(new SecureRandom());
        this.players = new JdbcPlayerDirectory(target);
    }

    public LiteBansSchemaReport inspect(DatabaseConfig source, String tablePrefix) {
        try (HikariDataSource database = MariaDb.open(source);
             Connection connection = database.getConnection()) {
            return new LiteBansSchemaInspector().inspect(connection, tablePrefix);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to inspect the LiteBans source schema", exception);
        }
    }

    public MigrationExecutionReport execute(
            DatabaseConfig source,
            String tablePrefix,
            int batchSize,
            MigrationMode mode
    ) {
        if (mode != MigrationMode.DRY_RUN && mode != MigrationMode.IMPORT && mode != MigrationMode.SHADOW) {
            throw new IllegalArgumentException("LiteBans execution supports DRY_RUN, IMPORT, or SHADOW");
        }
        UUID runId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        beginRun(runId, mode, tablePrefix, startedAt);
        try (HikariDataSource database = MariaDb.open(source);
             Connection connection = database.getConnection()) {
            LiteBansSchemaReport schema = new LiteBansSchemaInspector().inspect(connection, tablePrefix);
            if (!schema.importReady()) {
                failRun(runId, "SCHEMA_BLOCKED", schema.blockers().size());
                return new MigrationExecutionReport(
                        runId, mode, 0, 0, 0, emptyChecksum(), List.of(), schema, Optional.empty()
                );
            }
            LiteBansReadReport read = new LiteBansReader().read(connection, schema, batchSize);
            String checksum = new MigrationChecksum().calculate(read.records());
            long imported = 0;
            long replayed = 0;
            List<LiteBansReadReport.RejectedRow> rejected = new ArrayList<>(read.rejectedRows());
            if (mode != MigrationMode.DRY_RUN) {
                for (LegacySanction sanction : read.records()) {
                    ImportOutcome outcome = importOne(runId, sanction);
                    if (outcome == ImportOutcome.IMPORTED) {
                        imported++;
                    } else if (outcome == ImportOutcome.REPLAYED) {
                        replayed++;
                    } else {
                        rejected.add(new LiteBansReadReport.RejectedRow(
                                sanction.sourceTable(), sanction.externalId(), "UNRESOLVED_PLAYER_IDENTITY"
                        ));
                    }
                }
            }
            Optional<ShadowSummary> shadow = mode == MigrationMode.SHADOW
                    ? Optional.of(compare(runId, read.records(), rejected.size()))
                    : Optional.empty();
            completeRun(runId, read.records().size(), imported, replayed, checksum, rejected, shadow);
            return new MigrationExecutionReport(
                    runId,
                    mode,
                    read.records().size(),
                    imported,
                    replayed,
                    checksum,
                    rejected,
                    schema,
                    shadow
            );
        } catch (SQLException | RuntimeException exception) {
            failRun(runId, "EXECUTION_FAILED", 1);
            throw exception instanceof RuntimeException runtime
                    ? runtime
                    : new ModerationPersistenceException("LiteBans migration execution failed", exception);
        }
    }

    private ImportOutcome importOne(UUID runId, LegacySanction legacy) {
        Optional<UUID> resolved = legacy.playerId().or(() -> legacy.username()
                .flatMap(players::find)
                .map(PlayerIdentity::playerId));
        if (resolved.isEmpty()) {
            return ImportOutcome.REJECTED;
        }
        UUID targetId = resolved.orElseThrow();
        String checksum = new MigrationChecksum().calculate(List.of(legacy));
        Instant now = clock.instant();
        try (Connection connection = target.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (mappingExists(connection, legacy)) {
                    connection.rollback();
                    return ImportOutcome.REPLAYED;
                }
                ensurePlayer(connection, targetId, legacy.username(), legacy.issuedAt());
                CaseId caseId = identifiers.newCaseId();
                insertCase(connection, caseId, targetId, legacy);
                insertStep(connection, caseId);
                UUID sanctionId = insertSanction(connection, caseId, targetId, legacy, now);
                insertEvent(connection, sanctionId, legacy.issuedAt());
                insertMapping(connection, runId, legacy, caseId, checksum, now);
                insertAudit(connection, runId, caseId, targetId, legacy, sanctionId, now);
                connection.commit();
                return ImportOutcome.IMPORTED;
            } catch (SQLException | JsonProcessingException exception) {
                rollback(connection, exception);
                if (mappingExistsAfterConflict(legacy)) {
                    return ImportOutcome.REPLAYED;
                }
                throw new ModerationPersistenceException("Unable to import a LiteBans source record", exception);
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to open a LiteBans import transaction", exception);
        }
    }

    private void beginRun(UUID runId, MigrationMode mode, String prefix, Instant startedAt) {
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO migration_runs(run_id, mode, state, source_schema_name, started_at,
                         counts_json, checksums_json, report_json)
                     VALUES (?, ?, 'RUNNING', ?, ?, '{}', '{}', '{}')
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(runId));
            statement.setString(2, mode.name());
            statement.setString(3, "litebans:" + prefix);
            statement.setTimestamp(4, Timestamp.from(startedAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to begin migration run", exception);
        }
    }

    private void completeRun(
            UUID runId,
            long source,
            long imported,
            long replayed,
            String checksum,
            List<LiteBansReadReport.RejectedRow> rejected,
            Optional<ShadowSummary> shadow
    ) {
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE migration_runs
                     SET state = 'COMPLETED', completed_at = ?, counts_json = ?, checksums_json = ?,
                         mismatch_count = ?, report_json = ?
                     WHERE run_id = ? AND state = 'RUNNING'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(clock.instant()));
            statement.setString(2, json.writeValueAsString(Map.of(
                    "source", source, "imported", imported, "replayed", replayed, "rejected", rejected.size()
            )));
            statement.setString(3, json.writeValueAsString(Map.of("source", checksum)));
            statement.setLong(4, shadow.map(ShadowSummary::mismatchCount).orElse((long) rejected.size()));
            statement.setString(5, json.writeValueAsString(Map.of(
                    "rejected", rejected.stream().map(row -> Map.of(
                            "table", row.tableName(), "id", row.externalId(), "code", row.reasonCode()
                    )).toList()
            )));
            statement.setBytes(6, UuidBytes.toBytes(runId));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to complete migration run", exception);
        }
    }

    private ShadowSummary compare(UUID runId, List<LegacySanction> source, int rejectedRows) {
        Map<String, ImportedRow> imported = importedRows(source.stream().map(LegacySanction::sourceTable).collect(
                java.util.stream.Collectors.toSet()
        ));
        long checksumMismatches = 0;
        long activeMismatches = rejectedRows;
        long uuidMismatches = rejectedRows;
        long expirationMismatches = rejectedRows;
        long loginCompared = 0;
        long loginMismatched = 0;
        long muteCompared = 0;
        long muteMismatched = 0;
        long ipCompared = 0;
        long ipMismatched = 0;
        Instant now = clock.instant();
        Set<String> sourceKeys = new HashSet<>();
        for (LegacySanction legacy : source) {
            String key = key(legacy.sourceTable(), legacy.externalId());
            sourceKeys.add(key);
            ImportedRow row = imported.get(key);
            boolean expectedActive = legacy.active()
                    && legacy.expiresAt().map(expiration -> expiration.isAfter(now)).orElse(true);
            boolean activeMatch = row != null && row.active() == expectedActive;
            boolean typeMatch = row != null && row.type() == sanctionType(legacy.type());
            boolean decisionMatch = activeMatch && typeMatch;
            if (!new MigrationChecksum().calculate(List.of(legacy)).equals(row == null ? "" : row.checksum())) {
                checksumMismatches++;
            }
            if (!activeMatch) {
                activeMismatches++;
            }
            if (legacy.playerId().isPresent()
                    && (row == null || !legacy.playerId().orElseThrow().equals(row.targetId()))) {
                uuidMismatches++;
            }
            if (row == null || !legacy.expiresAt().equals(row.expiresAt())) {
                expirationMismatches++;
            }
            switch (legacy.type()) {
                case BAN -> {
                    loginCompared++;
                    if (!decisionMatch) {
                        loginMismatched++;
                    }
                }
                case MUTE -> {
                    muteCompared++;
                    if (!decisionMatch) {
                        muteMismatched++;
                    }
                }
                case IP_BAN -> {
                    ipCompared++;
                    if (!decisionMatch) {
                        ipMismatched++;
                    }
                }
            }
        }
        long extraMappings = imported.keySet().stream().filter(key -> !sourceKeys.contains(key)).count();
        boolean countsMatch = imported.size() == source.size() && rejectedRows == 0;
        long mismatchCount = checksumMismatches + activeMismatches + uuidMismatches + expirationMismatches
                + loginMismatched + muteMismatched + ipMismatched + extraMappings;
        ShadowSummary summary = new ShadowSummary(
                countsMatch,
                checksumMismatches == 0,
                activeMismatches == 0,
                uuidMismatches == 0,
                expirationMismatches == 0,
                new DecisionComparison(loginCompared, loginMismatched),
                new DecisionComparison(muteCompared, muteMismatched),
                new DecisionComparison(ipCompared, ipMismatched),
                mismatchCount
        );
        persistShadowSummary(runId, summary, now);
        return summary;
    }

    private Map<String, ImportedRow> importedRows(Set<String> sourceTables) {
        if (sourceTables.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceTables.size(), "?"));
        String sql = """
                SELECT m.source_table, m.external_id, m.source_checksum, c.target_id,
                       s.sanction_type, s.status, s.expiration_at
                FROM migration_mappings m
                JOIN cases c ON c.case_id = m.case_id
                JOIN sanctions s ON s.case_id = c.case_id
                WHERE m.source_system = 'LITEBANS' AND m.source_table IN (%s)
                """.formatted(placeholders);
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String table : sourceTables) {
                statement.setString(index++, table);
            }
            try (ResultSet result = statement.executeQuery()) {
                Map<String, ImportedRow> rows = new HashMap<>();
                while (result.next()) {
                    Timestamp expiration = result.getTimestamp("expiration_at");
                    rows.put(key(result.getString("source_table"), result.getString("external_id")), new ImportedRow(
                            result.getString("source_checksum"),
                            UuidBytes.fromBytes(result.getBytes("target_id")),
                            SanctionType.valueOf(result.getString("sanction_type")),
                            "ACTIVE".equals(result.getString("status")),
                            expiration == null ? Optional.empty() : Optional.of(expiration.toInstant())
                    ));
                }
                return Map.copyOf(rows);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read imported LiteBans mappings", exception);
        }
    }

    private void persistShadowSummary(UUID runId, ShadowSummary summary, Instant now) {
        Map<String, ComparisonValue> values = Map.of(
                "COUNTS", new ComparisonValue(summary.countsMatch(), 1, summary.countsMatch() ? 0 : 1),
                "CHECKSUMS", new ComparisonValue(summary.checksumsMatch(), 1, summary.checksumsMatch() ? 0 : 1),
                "ACTIVE_SANCTIONS", new ComparisonValue(summary.activeSanctionsMatch(), 1,
                        summary.activeSanctionsMatch() ? 0 : 1),
                "UUID_MAPPINGS", new ComparisonValue(summary.uuidMappingsMatch(), 1,
                        summary.uuidMappingsMatch() ? 0 : 1),
                "EXPIRATIONS", new ComparisonValue(summary.expirationsMatch(), 1,
                        summary.expirationsMatch() ? 0 : 1),
                "LOGIN_DECISIONS", new ComparisonValue(summary.loginDecisions().matches(),
                        summary.loginDecisions().compared(), summary.loginDecisions().mismatched()),
                "MUTE_DECISIONS", new ComparisonValue(summary.muteDecisions().matches(),
                        summary.muteDecisions().compared(), summary.muteDecisions().mismatched()),
                "IP_BAN_DECISIONS", new ComparisonValue(summary.ipBanDecisions().matches(),
                        summary.ipBanDecisions().compared(), summary.ipBanDecisions().mismatched())
        );
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO shadow_comparisons(comparison_id, run_id, comparison_type,
                         legacy_decision, expected_decision, matched, detail_json, compared_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            for (Map.Entry<String, ComparisonValue> entry : values.entrySet()) {
                statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                statement.setBytes(2, UuidBytes.toBytes(runId));
                statement.setString(3, entry.getKey());
                statement.setString(4, "LITEBANS");
                statement.setString(5, "ENTHUSIASTAFF");
                statement.setBoolean(6, entry.getValue().matched());
                statement.setString(7, json.writeValueAsString(Map.of(
                        "compared", entry.getValue().compared(), "mismatched", entry.getValue().mismatched()
                )));
                statement.setTimestamp(8, Timestamp.from(now));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to persist shadow comparison summary", exception);
        }
    }

    private static String key(String table, String externalId) {
        return table + '\u0000' + externalId;
    }

    private void failRun(UUID runId, String reason, long mismatches) {
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE migration_runs
                     SET state = 'FAILED', completed_at = ?, mismatch_count = ?, report_json = ?
                     WHERE run_id = ? AND state = 'RUNNING'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(clock.instant()));
            statement.setLong(2, mismatches);
            statement.setString(3, json.writeValueAsString(Map.of("reason", reason)));
            statement.setBytes(4, UuidBytes.toBytes(runId));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to persist migration failure", exception);
        }
    }

    private static boolean mappingExists(Connection connection, LegacySanction legacy) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM migration_mappings
                WHERE source_system = 'LITEBANS' AND source_table = ? AND external_id = ?
                """)) {
            statement.setString(1, legacy.sourceTable());
            statement.setString(2, legacy.externalId());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean mappingExistsAfterConflict(LegacySanction legacy) {
        try (Connection connection = target.getConnection()) {
            return mappingExists(connection, legacy);
        } catch (SQLException exception) {
            return false;
        }
    }

    private static void ensurePlayer(
            Connection connection,
            UUID targetId,
            Optional<String> username,
            Instant firstSeen
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO players(player_id, current_username, lowercase_username, first_seen_at, last_seen_at)
                VALUES (?, ?, LOWER(?), ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            if (username.isPresent()) {
                statement.setString(2, username.orElseThrow());
                statement.setString(3, username.orElseThrow());
            } else {
                statement.setNull(2, Types.VARCHAR);
                statement.setNull(3, Types.VARCHAR);
            }
            statement.setTimestamp(4, Timestamp.from(firstSeen));
            statement.setTimestamp(5, Timestamp.from(firstSeen));
            statement.executeUpdate();
        }
    }

    private static void insertCase(Connection connection, CaseId caseId, UUID targetId, LegacySanction legacy)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cases(case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                    public_reason, exact_reason_id, sanction_family, internal_explanation,
                    configuration_version, visibility, state, issued_at)
                VALUES (?, ?, ?, ?, ?, 'SYSTEM', ?, ?, 'legacy', ?, 'litebans-import-v1', 'PUBLIC', ?, ?)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, "litebans:" + legacy.sourceTable() + ':' + legacy.externalId());
            statement.setBytes(3, UuidBytes.toBytes(targetId));
            statement.setBytes(4, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setString(5, truncate(legacy.originalStaffName(), 64));
            statement.setString(6, truncate(legacy.originalReason(), 160));
            statement.setString(7, "legacy.litebans." + legacy.type().name().toLowerCase(java.util.Locale.ROOT));
            statement.setString(8, "Imported from LiteBans without changing the original reason or expiration");
            statement.setString(9, legacy.active() ? "OPEN" : "CLOSED");
            statement.setTimestamp(10, Timestamp.from(legacy.issuedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertStep(Connection connection, CaseId caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO punishment_steps(case_id, raw_ordinal, effective_ordinal, recency_bonus,
                    step_label, contribution_json, escalation_contributes)
                VALUES (?, 0, 0, 0, 'Imported LiteBans sanction', '[]', TRUE)
                """)) {
            statement.setString(1, caseId.value());
            statement.executeUpdate();
        }
    }

    private static UUID insertSanction(
            Connection connection,
            CaseId caseId,
            UUID targetId,
            LegacySanction legacy,
            Instant now
    ) throws SQLException {
        UUID sanctionId = UUID.randomUUID();
        boolean expired = legacy.expiresAt().filter(expiration -> !expiration.isAfter(now)).isPresent();
        boolean active = legacy.active() && !expired;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanctions(sanction_id, case_id, target_id, sanction_type, status,
                    issued_at, activated_at, expiration_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(sanctionId));
            statement.setString(2, caseId.value());
            statement.setBytes(3, UuidBytes.toBytes(targetId));
            statement.setString(4, sanctionType(legacy.type()).name());
            statement.setString(5, active ? "ACTIVE" : "EXPIRED");
            statement.setTimestamp(6, Timestamp.from(legacy.issuedAt()));
            statement.setTimestamp(7, Timestamp.from(legacy.issuedAt()));
            if (legacy.expiresAt().isPresent()) {
                statement.setTimestamp(8, Timestamp.from(legacy.expiresAt().orElseThrow()));
            } else {
                statement.setNull(8, Types.TIMESTAMP);
            }
            if (active) {
                statement.setNull(9, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(9, Timestamp.from(legacy.expiresAt().orElse(now)));
            }
            statement.executeUpdate();
        }
        return sanctionId;
    }

    private static void insertEvent(Connection connection, UUID sanctionId, Instant occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sanction_events(event_id, sanction_id, event_type, actor_id, occurred_at, event_json)
                VALUES (?, ?, 'IMPORTED', ?, ?, '{"source":"LITEBANS"}')
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(sanctionId));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setTimestamp(4, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }

    private static void insertMapping(
            Connection connection,
            UUID runId,
            LegacySanction legacy,
            CaseId caseId,
            String checksum,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO migration_mappings(mapping_id, run_id, source_system, source_table,
                    external_id, case_id, source_checksum, mapping_state, created_at)
                VALUES (?, ?, 'LITEBANS', ?, ?, ?, ?, 'IMPORTED', ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(runId));
            statement.setString(3, legacy.sourceTable());
            statement.setString(4, legacy.externalId());
            statement.setString(5, caseId.value());
            statement.setString(6, checksum);
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            UUID runId,
            CaseId caseId,
            UUID targetId,
            LegacySanction legacy,
            UUID sanctionId,
            Instant now
    ) throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, target_id, case_id,
                    event_type, outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'LITEBANS_RECORD_IMPORTED', 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(runId));
            statement.setBytes(3, UuidBytes.toBytes(SYSTEM_ACTOR));
            statement.setBytes(4, UuidBytes.toBytes(targetId));
            statement.setString(5, caseId.value());
            statement.setString(6, json.writeValueAsString(Map.of(
                    "sourceTable", legacy.sourceTable(),
                    "externalId", legacy.externalId(),
                    "sanctionId", sanctionId.toString()
            )));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static SanctionType sanctionType(LegacySanctionType type) {
        return switch (type) {
            case BAN -> SanctionType.NETWORK_BAN;
            case MUTE -> SanctionType.MUTE;
            case IP_BAN -> SanctionType.NETWORK_IDENTITY_BAN;
        };
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String emptyChecksum() {
        return new MigrationChecksum().calculate(List.of());
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
            // Closing returns the connection to the pool; the original failure stays authoritative.
        }
    }

    private enum ImportOutcome {
        IMPORTED,
        REPLAYED,
        REJECTED
    }

    private record ImportedRow(
            String checksum,
            UUID targetId,
            SanctionType type,
            boolean active,
            Optional<Instant> expiresAt
    ) {
    }

    private record ComparisonValue(boolean matched, long compared, long mismatched) {
    }
}
