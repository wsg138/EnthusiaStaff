package net.enthusia.staff.persistence.migration;

import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.migration.LegacySanction;

public record LiteBansReadReport(
        List<LegacySanction> records,
        List<LegacyNetworkObservation> networkObservations,
        List<RejectedRow> rejectedRows,
        Map<String, Long> sourceCounts,
        Map<String, Long> highWatermarks
) {
    public LiteBansReadReport {
        if (records == null || networkObservations == null || rejectedRows == null
                || sourceCounts == null || highWatermarks == null) {
            throw new IllegalArgumentException("LiteBans read report fields must be present");
        }
        records = List.copyOf(records);
        networkObservations = List.copyOf(networkObservations);
        rejectedRows = List.copyOf(rejectedRows);
        sourceCounts = Map.copyOf(sourceCounts);
        highWatermarks = Map.copyOf(highWatermarks);
        if (sourceCounts.values().stream().anyMatch(value -> value == null || value < 0)
                || highWatermarks.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("LiteBans counts and high-water marks cannot be negative");
        }
    }

    public record RejectedRow(String tableName, String externalId, String reasonCode) {
    }
}
