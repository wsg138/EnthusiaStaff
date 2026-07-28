package net.enthusia.staff.persistence.migration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import net.enthusia.staff.domain.migration.MigrationMode;

public record MigrationExecutionReport(
        UUID runId,
        MigrationMode mode,
        long sourceRecords,
        long importedRecords,
        long reconciledRecords,
        long replayedRecords,
        long networkIdentityRecords,
        long protectedIdentityRecords,
        String sourceChecksum,
        Map<String, Long> sourceCounts,
        Map<String, Long> sourceHighWatermarks,
        List<LiteBansReadReport.RejectedRow> rejectedRows,
        LiteBansSchemaReport schema,
        Optional<ShadowSummary> shadowSummary
) {
    public MigrationExecutionReport {
        if (runId == null || mode == null || sourceRecords < 0 || importedRecords < 0 || reconciledRecords < 0
                || replayedRecords < 0 || networkIdentityRecords < 0 || protectedIdentityRecords < 0
                || protectedIdentityRecords > networkIdentityRecords
                || sourceChecksum == null || sourceChecksum.isBlank()
                || sourceCounts == null || sourceHighWatermarks == null || rejectedRows == null || schema == null
                || shadowSummary == null) {
            throw new IllegalArgumentException("migration execution report fields must be present");
        }
        sourceCounts = Map.copyOf(sourceCounts);
        sourceHighWatermarks = Map.copyOf(sourceHighWatermarks);
        rejectedRows = List.copyOf(rejectedRows);
    }
}
