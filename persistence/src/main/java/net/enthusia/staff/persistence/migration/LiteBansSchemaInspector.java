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
    private static final String BANS = "bans";
    private static final String MUTES = "mutes";
    private static final String HISTORY = "history";
    private static final String UUID_COLUMN = "uuid";
    private static final String USERNAME_COLUMN = "username";
    private static final String STAFF_COLUMN = "staff";
    private static final String IP_COLUMN = "ip";
    private static final List<String> SANCTION_TABLES = List.of(BANS, MUTES);
    private static final List<String> AUDIT_ONLY_TABLES = List.of("kicks", "warnings");
    private static final List<String> BAN_IP_FLAG_ALIASES = List.of("ipban", "ip_ban");
    private static final List<String> NETWORK_ADDRESS_ALIASES = List.of(IP_COLUMN, "address");
    private static final List<String> BAN_STAFF_ALIASES = List.of(
            "banned_by_name", "staff_name", "executor"
    );
    private static final List<String> MUTE_STAFF_ALIASES = List.of(
            "muted_by_name", "staff_name", "executor"
    );
    private static final Map<String, List<String>> REQUIRED = Map.of(
            "id", List.of("id"),
            UUID_COLUMN, List.of(UUID_COLUMN),
            USERNAME_COLUMN, List.of("name", USERNAME_COLUMN),
            "reason", List.of("reason"),
            STAFF_COLUMN, List.of("banned_by_name", "muted_by_name", "staff_name", "executor"),
            "issued_at", List.of("time", "created_at"),
            "expires_at", List.of("until", "expires_at"),
            "active", List.of("active")
    );
    private static final List<String> REQUIRED_COLUMN_ORDER = List.of(
            "id", UUID_COLUMN, USERNAME_COLUMN, "reason", STAFF_COLUMN, "issued_at", "expires_at", "active"
    );
    private static final Map<String, List<String>> HISTORY_REQUIRED = Map.of(
            "id", List.of("id"),
            UUID_COLUMN, List.of(UUID_COLUMN),
            USERNAME_COLUMN, List.of("name", USERNAME_COLUMN),
            IP_COLUMN, NETWORK_ADDRESS_ALIASES,
            "observed_at", List.of("date", "observed_at", "created_at")
    );
    private static final List<String> HISTORY_COLUMN_ORDER = List.of(
            "id", UUID_COLUMN, USERNAME_COLUMN, IP_COLUMN, "observed_at"
    );

    public LiteBansSchemaReport inspect(Connection connection, String prefix) {
        if (connection == null || prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("invalid LiteBans connection or table prefix");
        }
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            Map<String, String> tables = tables(metadata, catalog);
            Map<String, LiteBansSchemaReport.TableMapping> importTables = new LinkedHashMap<>();
            Map<String, Set<String>> auditOnly = new LinkedHashMap<>();
            List<String> blockers = new ArrayList<>();
            inspectSanctionTables(metadata, catalog, prefix, tables, importTables, blockers);
            inspectHistoryTable(metadata, catalog, prefix, tables, importTables, blockers);
            inspectAuditOnlyTables(metadata, catalog, prefix, tables, auditOnly);
            return new LiteBansSchemaReport(prefix, importTables, auditOnly, blockers);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect LiteBans schema", exception);
        }
    }

    private static void inspectSanctionTables(
            DatabaseMetaData metadata,
            String catalog,
            String prefix,
            Map<String, String> tables,
            Map<String, LiteBansSchemaReport.TableMapping> importTables,
            List<String> blockers
    ) throws SQLException {
        for (String kind : SANCTION_TABLES) {
            inspectSanctionTable(metadata, catalog, prefix, kind, tables, importTables, blockers);
        }
    }

    private static void inspectSanctionTable(
            DatabaseMetaData metadata,
            String catalog,
            String prefix,
            String kind,
            Map<String, String> tables,
            Map<String, LiteBansSchemaReport.TableMapping> importTables,
            List<String> blockers
    ) throws SQLException {
        String expected = expectedTable(prefix, kind);
        String actual = tables.get(expected);
        if (actual == null) {
            blockers.add("MISSING_TABLE:" + expected);
            return;
        }
        Set<String> available = columns(metadata, catalog, actual);
        Map<String, String> resolved = resolveColumns(available, kind);
        addMissingColumns(actual, REQUIRED_COLUMN_ORDER, resolved, blockers);
        if (BANS.equals(kind)) {
            resolve(available, BAN_IP_FLAG_ALIASES).ifPresent(value -> resolved.put("ip_ban", value));
            resolve(available, NETWORK_ADDRESS_ALIASES).ifPresentOrElse(
                    value -> resolved.put(IP_COLUMN, value),
                    () -> blockers.add("MISSING_COLUMN:" + actual + ':' + IP_COLUMN)
            );
        }
        importTables.put(kind, new LiteBansSchemaReport.TableMapping(actual, resolved));
    }

    private static void inspectHistoryTable(
            DatabaseMetaData metadata,
            String catalog,
            String prefix,
            Map<String, String> tables,
            Map<String, LiteBansSchemaReport.TableMapping> importTables,
            List<String> blockers
    ) throws SQLException {
        String expected = expectedTable(prefix, HISTORY);
        String actual = tables.get(expected);
        if (actual == null) {
            blockers.add("MISSING_TABLE:" + prefix + HISTORY);
            return;
        }
        Set<String> available = columns(metadata, catalog, actual);
        Map<String, String> resolved = resolveRequired(available, HISTORY_REQUIRED);
        addMissingColumns(actual, HISTORY_COLUMN_ORDER, resolved, blockers);
        importTables.put(HISTORY, new LiteBansSchemaReport.TableMapping(actual, resolved));
    }

    private static void inspectAuditOnlyTables(
            DatabaseMetaData metadata,
            String catalog,
            String prefix,
            Map<String, String> tables,
            Map<String, Set<String>> auditOnly
    ) throws SQLException {
        for (String kind : AUDIT_ONLY_TABLES) {
            String actual = tables.get(expectedTable(prefix, kind));
            if (actual != null) {
                auditOnly.put(kind, columns(metadata, catalog, actual));
            }
        }
    }

    private static void addMissingColumns(
            String table,
            List<String> required,
            Map<String, String> resolved,
            List<String> blockers
    ) {
        for (String canonical : required) {
            if (!resolved.containsKey(canonical)) {
                blockers.add("MISSING_COLUMN:" + table + ':' + canonical);
            }
        }
    }

    private static String expectedTable(String prefix, String kind) {
        return (prefix + kind).toLowerCase(Locale.ROOT);
    }

    static Map<String, String> resolveColumns(Set<String> availableColumns, String kind) {
        Map<String, String> resolved = new LinkedHashMap<>();
        REQUIRED.forEach((canonical, aliases) -> resolve(availableColumns, aliases)
                .ifPresent(value -> resolved.put(canonical, value)));
        if (BANS.equals(kind)) {
            resolve(availableColumns, BAN_STAFF_ALIASES)
                    .ifPresent(value -> resolved.put(STAFF_COLUMN, value));
        } else if (MUTES.equals(kind)) {
            resolve(availableColumns, MUTE_STAFF_ALIASES)
                    .ifPresent(value -> resolved.put(STAFF_COLUMN, value));
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
