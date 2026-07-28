package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LiteBansSchemaInspector {
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");
    private static final Map<String, List<String>> REQUIRED = Map.of(
            "id", List.of("id"),
            "uuid", List.of("uuid"),
            "username", List.of("name", "username"),
            "reason", List.of("reason"),
            "staff", List.of("banned_by_name", "muted_by_name", "staff_name", "executor"),
            "issued_at", List.of("time", "created_at"),
            "expires_at", List.of("until", "expires_at"),
            "active", List.of("active")
    );
    private static final Map<String, List<String>> HISTORY_REQUIRED = Map.of(
            "id", List.of("id"),
            "uuid", List.of("uuid"),
            "username", List.of("name", "username"),
            "ip", List.of("ip", "address"),
            "observed_at", List.of("date", "observed_at", "created_at")
    );

    public LiteBansSchemaReport inspect(Connection connection, String prefix) {
        if (connection == null || prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("invalid LiteBans connection or table prefix");
        }
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, String> tables = tables(metadata, connection.getCatalog());
            Map<String, LiteBansSchemaReport.TableMapping> importTables = new LinkedHashMap<>();
            Map<String, Set<String>> auditOnly = new LinkedHashMap<>();
            List<String> blockers = new ArrayList<>();
            for (String kind : List.of("bans", "mutes")) {
                String expected = (prefix + kind).toLowerCase(Locale.ROOT);
                String actual = tables.get(expected);
                if (actual == null) {
                    blockers.add("MISSING_TABLE:" + expected);
                    continue;
                }
                Set<String> columns = columns(metadata, connection.getCatalog(), actual);
                Map<String, String> resolved = resolveColumns(columns, kind);
                for (String canonical : REQUIRED.keySet()) {
                    if (!resolved.containsKey(canonical)) {
                        blockers.add("MISSING_COLUMN:" + actual + ':' + canonical);
                    }
                }
                if (kind.equals("bans")) {
                    resolve(columns, List.of("ipban", "ip_ban")).ifPresent(value -> resolved.put("ip_ban", value));
                    resolve(columns, List.of("ip", "address")).ifPresentOrElse(
                            value -> resolved.put("ip", value),
                            () -> blockers.add("MISSING_COLUMN:" + actual + ":ip")
                    );
                }
                importTables.put(kind, new LiteBansSchemaReport.TableMapping(actual, resolved));
            }
            String historyTable = tables.get((prefix + "history").toLowerCase(Locale.ROOT));
            if (historyTable == null) {
                blockers.add("MISSING_TABLE:" + prefix + "history");
            } else {
                Set<String> historyColumns = columns(metadata, connection.getCatalog(), historyTable);
                Map<String, String> historyResolved = resolveRequired(historyColumns, HISTORY_REQUIRED);
                for (String canonical : HISTORY_REQUIRED.keySet()) {
                    if (!historyResolved.containsKey(canonical)) {
                        blockers.add("MISSING_COLUMN:" + historyTable + ':' + canonical);
                    }
                }
                importTables.put("history", new LiteBansSchemaReport.TableMapping(historyTable, historyResolved));
            }
            for (String kind : List.of("kicks", "warnings")) {
                String actual = tables.get((prefix + kind).toLowerCase(Locale.ROOT));
                if (actual != null) {
                    auditOnly.put(kind, columns(metadata, connection.getCatalog(), actual));
                }
            }
            return new LiteBansSchemaReport(prefix, importTables, auditOnly, blockers);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect LiteBans schema", exception);
        }
    }

    static Map<String, String> resolveColumns(Set<String> availableColumns, String kind) {
        Map<String, String> resolved = new LinkedHashMap<>();
        REQUIRED.forEach((canonical, aliases) -> resolve(availableColumns, aliases)
                .ifPresent(value -> resolved.put(canonical, value)));
        if (kind.equals("bans")) {
            resolve(availableColumns, List.of("banned_by_name", "staff_name", "executor"))
                    .ifPresent(value -> resolved.put("staff", value));
        } else if (kind.equals("mutes")) {
            resolve(availableColumns, List.of("muted_by_name", "staff_name", "executor"))
                    .ifPresent(value -> resolved.put("staff", value));
        }
        resolve(availableColumns, List.of("removed_by_date", "removed_at", "ended_at"))
                .ifPresent(value -> resolved.put("ended_at", value));
        return resolved;
    }

    private static Map<String, String> resolveRequired(
            Set<String> availableColumns,
            Map<String, List<String>> required
    ) {
        Map<String, String> resolved = new LinkedHashMap<>();
        required.forEach((canonical, aliases) -> resolve(availableColumns, aliases)
                .ifPresent(value -> resolved.put(canonical, value)));
        return resolved;
    }

    private static java.util.Optional<String> resolve(Set<String> columns, List<String> aliases) {
        for (String alias : aliases) {
            for (String column : columns) {
                if (column.equalsIgnoreCase(alias)) {
                    return java.util.Optional.of(column);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private static Map<String, String> tables(DatabaseMetaData metadata, String catalog) throws SQLException {
        Map<String, String> tables = new LinkedHashMap<>();
        try (ResultSet results = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (results.next()) {
                String name = results.getString("TABLE_NAME");
                tables.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return tables;
    }

    private static Set<String> columns(DatabaseMetaData metadata, String catalog, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet results = metadata.getColumns(catalog, null, table, "%")) {
            while (results.next()) {
                columns.add(results.getString("COLUMN_NAME"));
            }
        }
        return Set.copyOf(columns);
    }
}
