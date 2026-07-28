package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.security.NetworkIdentityProtector;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.MigrationChecksum;
import net.enthusia.staff.domain.migration.MigrationMode;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.UuidBytes;

public final class LiteBansMigrationService {
    private final DataSource target;
    private final ObjectMapper json;
    private final Clock clock;
    private final LiteBansTargetImporter targetImporter;
    private final LiteBansShadowComparator shadowComparator;

    public LiteBansMigrationService(DataSource target, ObjectMapper json, Clock clock) {
        this(target, json, clock, null);
    }

    public LiteBansMigrationService(
            DataSource target,
            ObjectMapper json,
            Clock clock,
            NetworkIdentityProtector networkIdentityProtector
    ) {
        if (target == null || json == null || clock == null) {
            throw new IllegalArgumentException("migration dependencies must be present");
        }
        this.target = target;
        this.json = json;
        this.clock = clock;
        this.targetImporter = new LiteBansTargetImporter(target, json, clock, networkIdentityProtector);
        this.shadowComparator = new LiteBansShadowComparator(target, json, clock, targetImporter);
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
        if (mode == null) {
            throw new IllegalArgumentException("LiteBans migration mode must be present");
        }
        MigrationDatabaseLock migrationLock = MigrationDatabaseLock.acquire(target);
        Throwable operationFailure = null;
        try {
            recoverAbandonedRuns();
            requireAllowedOperationalMode(mode);
            UUID runId = UUID.randomUUID();
            Instant startedAt = clock.instant();
            beginRun(runId, mode, tablePrefix, startedAt);
            try (HikariDataSource database = MariaDb.open(source);
                 Connection connection = database.getConnection()) {
                configureStableSourceSnapshot(connection);
                LiteBansSchemaReport schema = new LiteBansSchemaInspector().inspect(connection, tablePrefix);
                if (!schema.importReady()) {
                    failRun(runId, "SCHEMA_BLOCKED", schema.blockers().size());
                    return new MigrationExecutionReport(
                            runId,
                            mode,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            emptyChecksum(),
                            Map.of(),
                            Map.of(),
                            List.of(),
                            schema,
                            Optional.empty()
                    );
                }
                LiteBansReadReport read = new LiteBansReader().read(connection, schema, batchSize);
                String checksum = new MigrationChecksum().calculate(read.records());
                long imported = 0;
                long reconciled = 0;
                long replayed = 0;
                long protectedIdentityRecords = 0;
                List<LiteBansReadReport.RejectedRow> rejected = new ArrayList<>(read.rejectedRows());
                if (mode != MigrationMode.DRY_RUN) {
                    LiteBansTargetImporter.TargetImportReport importReport =
                            targetImporter.importAll(runId, read, batchSize);
                    imported = importReport.imported();
                    reconciled = importReport.reconciled();
                    replayed = importReport.replayed();
                    protectedIdentityRecords = importReport.protectedIdentityRecords();
                    rejected.addAll(importReport.rejectedRows());
                }
                Optional<ShadowSummary> shadow = mode == MigrationMode.SHADOW || mode == MigrationMode.CUTOVER
                        ? Optional.of(shadowComparator.compare(runId, read, rejected.size()))
                        : Optional.empty();
                completeRun(
                        runId,
                        read,
                        imported,
                        reconciled,
                        replayed,
                        protectedIdentityRecords,
                        checksum,
                        rejected,
                        shadow
                );
                return new MigrationExecutionReport(
                        runId,
                        mode,
                        read.records().size(),
                        imported,
                        reconciled,
                        replayed,
                        read.networkObservations().size(),
                        protectedIdentityRecords,
                        checksum,
                        read.sourceCounts(),
                        read.highWatermarks(),
                        rejected,
                        schema,
                        shadow
                );
            } catch (SQLException exception) {
                recordRunFailure(runId, exception);
                throw new ModerationPersistenceException("LiteBans migration execution failed", exception);
            } catch (RuntimeException exception) {
                recordRunFailure(runId, exception);
                throw exception;
            }
        } catch (RuntimeException | Error exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            migrationLock.closeAfter(operationFailure);
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

    private void requireAllowedOperationalMode(MigrationMode migrationMode) {
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT mode FROM operational_state WHERE singleton_id = 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new ModerationPersistenceException("Operational state singleton is missing");
            }
            OperationalMode operationalMode = OperationalMode.valueOf(result.getString(1));
            boolean allowed = switch (migrationMode) {
                case DRY_RUN -> true;
                case IMPORT -> operationalMode == OperationalMode.SHADOW_MIGRATION
                        || operationalMode == OperationalMode.MAINTENANCE;
                case SHADOW -> operationalMode == OperationalMode.SHADOW_MIGRATION;
                case CUTOVER -> operationalMode == OperationalMode.MAINTENANCE;
            };
            if (!allowed) {
                throw new IllegalStateException(
                        migrationMode + " migration is not allowed while the runtime is " + operationalMode
                );
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to verify migration operational mode", exception);
        }
    }

    private void recoverAbandonedRuns() {
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE migration_runs
                     SET state = 'FAILED', completed_at = ?, mismatch_count = mismatch_count + 1,
                         report_json = ?
                     WHERE state = 'RUNNING'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(clock.instant()));
            statement.setString(2, "{\"reason\":\"ABANDONED_AFTER_PROCESS_FAILURE\"}");
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to recover abandoned migration runs", exception);
        }
    }

    private static void configureStableSourceSnapshot(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
    }

    private void recordRunFailure(UUID runId, Exception original) {
        try {
            failRun(runId, "EXECUTION_FAILED", 1);
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

    private static Map<String, String> rejectedRowJson(LiteBansReadReport.RejectedRow row) {
        return Map.of(
                "table", row.tableName(),
                "id", row.externalId(),
                "code", row.reasonCode()
        );
    }

    private void completeRun(
            UUID runId,
            LiteBansReadReport read,
            long imported,
            long reconciled,
            long replayed,
            long protectedIdentityRecords,
            String checksum,
            List<LiteBansReadReport.RejectedRow> rejected,
            Optional<ShadowSummary> shadow
    ) {
        try (Connection connection = target.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE migration_runs
                     SET state = 'COMPLETED', completed_at = ?, counts_json = ?, checksums_json = ?,
                         source_high_watermark = ?, mismatch_count = ?, report_json = ?
                     WHERE run_id = ? AND state = 'RUNNING'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(clock.instant()));
            statement.setString(2, json.writeValueAsString(Map.of(
                    "source", read.records().size(),
                    "sourceByTable", read.sourceCounts(),
                    "imported", imported,
                    "reconciled", reconciled,
                    "replayed", replayed,
                    "networkIdentitySource", read.networkObservations().size(),
                    "networkIdentityProtected", protectedIdentityRecords,
                    "rejected", rejected.size()
            )));
            statement.setString(3, json.writeValueAsString(Map.of("source", checksum)));
            statement.setLong(4, read.highWatermarks().values().stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0));
            statement.setLong(5, shadow.map(ShadowSummary::mismatchCount).orElse((long) rejected.size()));
            statement.setString(6, json.writeValueAsString(Map.of(
                    "highWatermarks", read.highWatermarks(),
                    "rejected", rejected.stream().map(LiteBansMigrationService::rejectedRowJson).toList()
            )));
            statement.setBytes(7, UuidBytes.toBytes(runId));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("migration run was not in RUNNING state during completion");
            }
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to complete migration run", exception);
        }
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

    private static String emptyChecksum() {
        return new MigrationChecksum().calculate(List.of());
    }
}
