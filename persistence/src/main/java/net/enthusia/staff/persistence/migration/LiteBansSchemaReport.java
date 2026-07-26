package net.enthusia.staff.persistence.migration;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record LiteBansSchemaReport(
        String prefix,
        Map<String, TableMapping> importTables,
        Map<String, Set<String>> auditOnlyTables,
        List<String> blockers
) {
    public LiteBansSchemaReport {
        importTables = Map.copyOf(importTables);
        auditOnlyTables = Map.copyOf(auditOnlyTables);
        blockers = List.copyOf(blockers);
    }

    public boolean importReady() {
        return blockers.isEmpty() && importTables.keySet().containsAll(Set.of("bans", "mutes"));
    }

    public record TableMapping(String tableName, Map<String, String> canonicalColumns) {
        public TableMapping {
            canonicalColumns = Map.copyOf(canonicalColumns);
        }
    }
}
