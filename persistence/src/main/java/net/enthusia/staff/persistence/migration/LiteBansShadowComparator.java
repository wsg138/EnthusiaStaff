package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.migration.DecisionComparison;
import net.enthusia.staff.domain.migration.LegacySanction;
import net.enthusia.staff.domain.migration.LegacySanctionType;
import net.enthusia.staff.domain.migration.MigrationChecksum;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import net.enthusia.staff.persistence.UuidBytes;

final class LiteBansShadowComparator {
    private final DataSource target;
    private final ObjectMapper json;
    private final Clock clock;
    private final LiteBansTargetImporter targetImporter;

    LiteBansShadowComparator(
            DataSource target,
            ObjectMapper json,
            Clock clock,
            LiteBansTargetImporter targetImporter
    ) {
        if (target == null || json == null || clock == null || targetImporter == null) {
            throw new IllegalArgumentException("shadow comparison dependencies must be present");
        }
        this.target = target;
        this.json = json;
        this.clock = clock;
        this.targetImporter = targetImporter;
    }

    ShadowSummary compare(UUID runId, LiteBansReadReport read, int rejectedRows) {
        List<LegacySanction> source = read.records();
        Map<String, ImportedRow> imported = importedRows(read.sourceCounts().keySet());
        Instant now = clock.instant();
        ShadowMetrics metrics = new ShadowMetrics(rejectedRows, now, targetImporter);
        Set<String> sourceKeys = new HashSet<>();
        for (LegacySanction legacy : source) {
            String key = key(legacy.sourceTable(), legacy.externalId());
            sourceKeys.add(key);
            metrics.compare(legacy, imported.get(key));
        }
        long extraMappings = imported.keySet().stream().filter(key -> !sourceKeys.contains(key)).count();
        ShadowSummary summary = metrics.summary(source.size(), imported.size(), extraMappings);
        persist(runId, summary, now);
        return summary;
    }

    private Map<String, ImportedRow> importedRows(Set<String> sourceTables) {
        if (sourceTables.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(sourceTables.size(), "?"));
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
                statement.setString(index, table);
                index++;
            }
            try (ResultSet result = statement.executeQuery()) {
                Map<String, ImportedRow> rows = new HashMap<>();
                while (result.next()) {
                    rows.put(
                            key(result.getString("source_table"), result.getString("external_id")),
                            importedRow(result)
                    );
                }
                return Map.copyOf(rows);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read imported LiteBans mappings", exception);
        }
    }

    private static ImportedRow importedRow(ResultSet result) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        return new ImportedRow(
                result.getString("source_checksum"),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                SanctionType.valueOf(result.getString("sanction_type")),
                "ACTIVE".equals(result.getString("status")),
                expiration == null ? Optional.empty() : Optional.of(expiration.toInstant())
        );
    }

    private void persist(UUID runId, ShadowSummary summary, Instant now) {
        Map<String, ComparisonValue> values = Map.of(
                "COUNTS", binaryComparison(summary.countsMatch()),
                "CHECKSUMS", binaryComparison(summary.checksumsMatch()),
                "ACTIVE_SANCTIONS", binaryComparison(summary.activeSanctionsMatch()),
                "UUID_MAPPINGS", binaryComparison(summary.uuidMappingsMatch()),
                "EXPIRATIONS", binaryComparison(summary.expirationsMatch()),
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
                        "compared", entry.getValue().compared(),
                        "mismatched", entry.getValue().mismatched()
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

    private static final class ShadowMetrics {
        private final int rejectedRows;
        private final Instant now;
        private final LiteBansTargetImporter targetImporter;
        private final MigrationChecksum checksum = new MigrationChecksum();
        private final EnumMap<LegacySanctionType, DecisionCounter> decisions =
                new EnumMap<>(LegacySanctionType.class);
        private long checksumMismatches;
        private long activeMismatches;
        private long uuidMismatches;
        private long expirationMismatches;

        private ShadowMetrics(int rejectedRows, Instant now, LiteBansTargetImporter targetImporter) {
            this.rejectedRows = rejectedRows;
            this.now = now;
            this.targetImporter = targetImporter;
            this.activeMismatches = rejectedRows;
            this.uuidMismatches = rejectedRows;
            this.expirationMismatches = rejectedRows;
            decisions.put(LegacySanctionType.BAN, new DecisionCounter());
            decisions.put(LegacySanctionType.MUTE, new DecisionCounter());
            decisions.put(LegacySanctionType.IP_BAN, new DecisionCounter());
        }

        private void compare(LegacySanction legacy, ImportedRow row) {
            RowComparison comparison = comparison(legacy, row);
            if (!comparison.checksumMatches()) {
                checksumMismatches++;
            }
            if (!comparison.activeMatches()) {
                activeMismatches++;
            }
            if (!comparison.uuidMatches()) {
                uuidMismatches++;
            }
            if (!comparison.expirationMatches()) {
                expirationMismatches++;
            }
            decisionCounter(legacy.type()).record(comparison.decisionMatches());
        }

        private RowComparison comparison(LegacySanction legacy, ImportedRow row) {
            boolean activeMatches = activeMatches(legacy, row);
            boolean typeMatches = row != null
                    && row.type() == LiteBansTargetImporter.sanctionType(legacy.type());
            return new RowComparison(
                    row != null && checksum.calculate(List.of(legacy)).equals(row.checksum()),
                    activeMatches,
                    uuidMatches(legacy, row),
                    row != null && legacy.expiresAt().equals(row.expiresAt()),
                    activeMatches && typeMatches && networkIdentityMatches(legacy, row)
            );
        }

        private boolean activeMatches(LegacySanction legacy, ImportedRow row) {
            boolean expectedActive = legacy.active()
                    && legacy.expiresAt().map(expiration -> expiration.isAfter(now)).orElse(true);
            return row != null && row.active() == expectedActive;
        }

        private static boolean uuidMatches(LegacySanction legacy, ImportedRow row) {
            return legacy.playerId().isEmpty()
                    || row != null && legacy.playerId().orElseThrow().equals(row.targetId());
        }

        private boolean networkIdentityMatches(LegacySanction legacy, ImportedRow row) {
            if (legacy.type() != LegacySanctionType.IP_BAN) {
                return true;
            }
            return targetImporter.protectedIdentityExists(
                    row.targetId(), legacy.networkAddress().orElseThrow()
            );
        }

        private DecisionCounter decisionCounter(LegacySanctionType type) {
            DecisionCounter counter = decisions.get(type);
            if (counter == null) {
                throw new IllegalStateException("Unsupported legacy sanction type: " + type);
            }
            return counter;
        }

        private ShadowSummary summary(int sourceRows, int importedRows, long extraMappings) {
            DecisionCounter login = decisionCounter(LegacySanctionType.BAN);
            DecisionCounter mute = decisionCounter(LegacySanctionType.MUTE);
            DecisionCounter ipBan = decisionCounter(LegacySanctionType.IP_BAN);
            long mismatchCount = checksumMismatches + activeMismatches + uuidMismatches
                    + expirationMismatches + login.mismatched + mute.mismatched
                    + ipBan.mismatched + extraMappings;
            return new ShadowSummary(
                    importedRows == sourceRows && rejectedRows == 0,
                    checksumMismatches == 0,
                    activeMismatches == 0,
                    uuidMismatches == 0,
                    expirationMismatches == 0,
                    login.comparison(),
                    mute.comparison(),
                    ipBan.comparison(),
                    mismatchCount
            );
        }
    }

    private static ComparisonValue binaryComparison(boolean matched) {
        return new ComparisonValue(matched, 1, matched ? 0 : 1);
    }

    private static final class DecisionCounter {
        private long compared;
        private long mismatched;

        private void record(boolean matched) {
            compared++;
            if (!matched) {
                mismatched++;
            }
        }

        private DecisionComparison comparison() {
            return new DecisionComparison(compared, mismatched);
        }
    }

    private record RowComparison(
            boolean checksumMatches,
            boolean activeMatches,
            boolean uuidMatches,
            boolean expirationMatches,
            boolean decisionMatches
    ) {
    }
}
