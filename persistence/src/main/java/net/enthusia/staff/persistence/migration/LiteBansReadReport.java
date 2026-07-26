package net.enthusia.staff.persistence.migration;

import java.util.List;
import net.enthusia.staff.domain.migration.LegacySanction;

public record LiteBansReadReport(List<LegacySanction> records, List<RejectedRow> rejectedRows) {
    public LiteBansReadReport {
        records = List.copyOf(records);
        rejectedRows = List.copyOf(rejectedRows);
    }

    public record RejectedRow(String tableName, String externalId, String reasonCode) {
    }
}
