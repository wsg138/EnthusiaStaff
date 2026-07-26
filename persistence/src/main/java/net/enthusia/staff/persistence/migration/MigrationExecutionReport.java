package net.enthusia.staff.persistence.migration;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import net.enthusia.staff.domain.migration.MigrationMode;

public record MigrationExecutionReport(
        UUID runId,
        MigrationMode mode,
        long sourceRecords,
        long importedRecords,
        long replayedRecords,
        String sourceChecksum,
        List<LiteBansReadReport.RejectedRow> rejectedRows,
        LiteBansSchemaReport schema,
        Optional<ShadowSummary> shadowSummary
) {
    public MigrationExecutionReport {
        if (runId == null || mode == null || sourceRecords < 0 || importedRecords < 0 || replayedRecords < 0
                || sourceChecksum == null || sourceChecksum.isBlank() || rejectedRows == null || schema == null
                || shadowSummary == null) {
            throw new IllegalArgumentException("migration execution report fields must be present");
        }
        rejectedRows = List.copyOf(rejectedRows);
    }
}
