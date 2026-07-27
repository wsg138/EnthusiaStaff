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
            boolean typeMatch = row != null && row.type() == LiteBansTargetImporter.sanctionType(legacy.type());
            boolean decisionMatch = activeMatch && typeMatch;
            if (legacy.type() == LegacySanctionType.IP_BAN) {
                decisionMatch = decisionMatch && row != null
                        && targetImporter.protectedIdentityExists(
                                row.targetId(), legacy.networkAddress().orElseThrow()
                        );
            }
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

    private void persist(UUID runId, ShadowSummary summary, Instant now) {
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
}
