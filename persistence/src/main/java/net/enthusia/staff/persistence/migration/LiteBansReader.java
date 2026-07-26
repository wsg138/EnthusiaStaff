package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.migration.LegacySanction;
import net.enthusia.staff.domain.migration.LegacySanctionType;

public final class LiteBansReader {
    private static final int MAX_BATCH_SIZE = 5_000;

    public LiteBansReadReport read(
            Connection connection,
            LiteBansSchemaReport report,
            int batchSize
    ) {
        if (connection == null || report == null || !report.importReady()) {
            throw new IllegalArgumentException("a ready inspected schema is required");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
        List<LegacySanction> records = new ArrayList<>();
        List<LiteBansReadReport.RejectedRow> rejected = new ArrayList<>();
        readTable(connection, report.importTables().get("bans"), LegacySanctionType.BAN, batchSize, records, rejected);
        readTable(connection, report.importTables().get("mutes"), LegacySanctionType.MUTE, batchSize, records, rejected);
        return new LiteBansReadReport(records, rejected);
    }

    private static void readTable(
            Connection connection,
            LiteBansSchemaReport.TableMapping mapping,
            LegacySanctionType defaultType,
            int batchSize,
            List<LegacySanction> records,
            List<LiteBansReadReport.RejectedRow> rejected
    ) {
        long lastId = Long.MIN_VALUE;
        while (true) {
            List<Row> rows = readBatch(connection, mapping, lastId, batchSize);
            if (rows.isEmpty()) {
                return;
            }
            for (Row row : rows) {
                lastId = Math.max(lastId, row.id());
                try {
                    records.add(toSanction(mapping.tableName(), row, defaultType));
                } catch (IllegalArgumentException | DateTimeException exception) {
                    rejected.add(new LiteBansReadReport.RejectedRow(
                            mapping.tableName(), Long.toString(row.id()), "INVALID_SOURCE_ROW"));
                }
            }
        }
    }

    private static List<Row> readBatch(
            Connection connection,
            LiteBansSchemaReport.TableMapping mapping,
            long lastId,
            int batchSize
    ) {
        Map<String, String> columns = mapping.canonicalColumns();
        String sql = "SELECT " + selectList(columns) + " FROM " + quote(mapping.tableName())
                + " WHERE " + quote(columns.get("id")) + " > ? ORDER BY " + quote(columns.get("id")) + " LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lastId);
            statement.setInt(2, batchSize);
            try (ResultSet results = statement.executeQuery()) {
                List<Row> rows = new ArrayList<>();
                while (results.next()) {
                    rows.add(new Row(
                            results.getLong("source_id"),
                            results.getString("source_uuid"),
                            results.getString("source_username"),
                            results.getString("source_reason"),
                            results.getString("source_staff"),
                            results.getLong("source_issued_at"),
                            results.getLong("source_expires_at"),
                            results.getBoolean("source_active"),
                            columns.containsKey("ip_ban") && results.getBoolean("source_ip_ban")
                    ));
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read inspected LiteBans table " + mapping.tableName(), exception);
        }
    }

    private static LegacySanction toSanction(String table, Row row, LegacySanctionType defaultType) {
        Optional<UUID> playerId = optionalUuid(row.uuid());
        Optional<String> username = Optional.ofNullable(row.username()).filter(value -> !value.isBlank());
        LegacySanctionType type = row.ipBan() ? LegacySanctionType.IP_BAN : defaultType;
        Optional<Instant> expiration = row.expiresAt() <= 0 || row.expiresAt() == Long.MAX_VALUE
                ? Optional.empty()
                : Optional.of(Instant.ofEpochMilli(row.expiresAt()));
        return new LegacySanction(
                table,
                Long.toString(row.id()),
                type,
                playerId,
                username,
                defaultString(row.reason(), "Imported legacy punishment"),
                defaultString(row.staff(), "Legacy staff"),
                Instant.ofEpochMilli(row.issuedAt()),
                expiration,
                row.active()
        );
    }

    private static Optional<UUID> optionalUuid(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("CONSOLE")) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value));
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String selectList(Map<String, String> columns) {
        List<String> selection = new ArrayList<>();
        selection.add(quote(columns.get("id")) + " AS source_id");
        selection.add(quote(columns.get("uuid")) + " AS source_uuid");
        selection.add(quote(columns.get("username")) + " AS source_username");
        selection.add(quote(columns.get("reason")) + " AS source_reason");
        selection.add(quote(columns.get("staff")) + " AS source_staff");
        selection.add(quote(columns.get("issued_at")) + " AS source_issued_at");
        selection.add(quote(columns.get("expires_at")) + " AS source_expires_at");
        selection.add(quote(columns.get("active")) + " AS source_active");
        if (columns.containsKey("ip_ban")) {
            selection.add(quote(columns.get("ip_ban")) + " AS source_ip_ban");
        }
        return String.join(", ", selection);
    }

    private static String quote(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("unsafe inspected SQL identifier");
        }
        return '`' + identifier + '`';
    }

    private record Row(
            long id,
            String uuid,
            String username,
            String reason,
            String staff,
            long issuedAt,
            long expiresAt,
            boolean active,
            boolean ipBan
    ) {
    }
}
