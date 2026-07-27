package net.enthusia.staff.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.migration.LegacyNetworkAddress;
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
        List<LegacyNetworkObservation> observations = new ArrayList<>();
        List<LiteBansReadReport.RejectedRow> rejected = new ArrayList<>();
        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        Map<String, Long> highWatermarks = new LinkedHashMap<>();
        readTable(
                connection,
                report.importTables().get("bans"),
                LegacySanctionType.BAN,
                batchSize,
                records,
                rejected,
                sourceCounts,
                highWatermarks
        );
        readTable(
                connection,
                report.importTables().get("mutes"),
                LegacySanctionType.MUTE,
                batchSize,
                records,
                rejected,
                sourceCounts,
                highWatermarks
        );
        readHistory(
                connection,
                report.importTables().get("history"),
                batchSize,
                observations,
                rejected,
                sourceCounts,
                highWatermarks
        );
        return new LiteBansReadReport(records, observations, rejected, sourceCounts, highWatermarks);
    }

    private static void readTable(
            Connection connection,
            LiteBansSchemaReport.TableMapping mapping,
            LegacySanctionType defaultType,
            int batchSize,
            List<LegacySanction> records,
            List<LiteBansReadReport.RejectedRow> rejected,
            Map<String, Long> sourceCounts,
            Map<String, Long> highWatermarks
    ) {
        long lastId = -1;
        long count = 0;
        long highWatermark = 0;
        while (true) {
            List<Row> rows = readBatch(connection, mapping, lastId, batchSize);
            if (rows.isEmpty()) {
                sourceCounts.put(mapping.tableName(), count);
                highWatermarks.put(mapping.tableName(), highWatermark);
                return;
            }
            for (Row row : rows) {
                lastId = Math.max(lastId, row.id());
                highWatermark = Math.max(highWatermark, row.id());
                count++;
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
                            columns.containsKey("ip_ban") && results.getBoolean("source_ip_ban"),
                            columns.containsKey("ended_at")
                                    ? optionalInstant(results.getObject("source_ended_at"))
                                    : Optional.empty(),
                            columns.containsKey("ip") ? results.getString("source_ip") : null
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
        Optional<LegacyNetworkAddress> networkAddress = row.ipBan()
                ? Optional.of(parseNetworkAddress(row.ip()))
                : Optional.empty();
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
                row.endedAt(),
                networkAddress,
                row.active()
        );
    }

    private static Optional<UUID> optionalUuid(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("CONSOLE")) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.matches("[0-9A-Fa-f]{32}")) {
            normalized = normalized.substring(0, 8) + '-' + normalized.substring(8, 12) + '-'
                    + normalized.substring(12, 16) + '-' + normalized.substring(16, 20) + '-'
                    + normalized.substring(20);
        }
        UUID parsed = UUID.fromString(normalized);
        if (!parsed.toString().equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("non-canonical legacy UUID");
        }
        return Optional.of(parsed);
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
        if (columns.containsKey("ip")) {
            selection.add(quote(columns.get("ip")) + " AS source_ip");
        }
        if (columns.containsKey("ended_at")) {
            selection.add(quote(columns.get("ended_at")) + " AS source_ended_at");
        }
        return String.join(", ", selection);
    }

    private static void readHistory(
            Connection connection,
            LiteBansSchemaReport.TableMapping mapping,
            int batchSize,
            List<LegacyNetworkObservation> observations,
            List<LiteBansReadReport.RejectedRow> rejected,
            Map<String, Long> sourceCounts,
            Map<String, Long> highWatermarks
    ) {
        long lastId = -1;
        long count = 0;
        long highWatermark = 0;
        while (true) {
            List<HistoryRow> rows = readHistoryBatch(connection, mapping, lastId, batchSize);
            if (rows.isEmpty()) {
                sourceCounts.put(mapping.tableName(), count);
                highWatermarks.put(mapping.tableName(), highWatermark);
                return;
            }
            for (HistoryRow row : rows) {
                lastId = Math.max(lastId, row.id());
                highWatermark = Math.max(highWatermark, row.id());
                count++;
                try {
                    UUID playerId = optionalUuid(row.uuid()).orElseThrow(
                            () -> new IllegalArgumentException("LiteBans history UUID is missing")
                    );
                    Optional<String> username = Optional.ofNullable(row.username())
                            .filter(value -> value.matches("[A-Za-z0-9_]{1,32}"));
                    observations.add(new LegacyNetworkObservation(
                            mapping.tableName(),
                            Long.toString(row.id()),
                            playerId,
                            username,
                            parseNetworkAddress(row.ip()),
                            row.observedAt()
                    ));
                } catch (IllegalArgumentException | DateTimeException exception) {
                    rejected.add(new LiteBansReadReport.RejectedRow(
                            mapping.tableName(), Long.toString(row.id()), "INVALID_HISTORY_ROW"
                    ));
                }
            }
        }
    }

    private static List<HistoryRow> readHistoryBatch(
            Connection connection,
            LiteBansSchemaReport.TableMapping mapping,
            long lastId,
            int batchSize
    ) {
        Map<String, String> columns = mapping.canonicalColumns();
        String sql = "SELECT "
                + quote(columns.get("id")) + " AS source_id, "
                + quote(columns.get("uuid")) + " AS source_uuid, "
                + quote(columns.get("username")) + " AS source_username, "
                + quote(columns.get("ip")) + " AS source_ip, "
                + quote(columns.get("observed_at")) + " AS source_observed_at FROM "
                + quote(mapping.tableName()) + " WHERE " + quote(columns.get("id")) + " > ? ORDER BY "
                + quote(columns.get("id")) + " LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lastId);
            statement.setInt(2, batchSize);
            try (ResultSet result = statement.executeQuery()) {
                List<HistoryRow> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(new HistoryRow(
                            result.getLong("source_id"),
                            result.getString("source_uuid"),
                            result.getString("source_username"),
                            result.getString("source_ip"),
                            optionalInstant(result.getObject("source_observed_at")).orElse(null)
                    ));
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read inspected LiteBans history table", exception);
        }
    }

    static LegacyNetworkAddress parseNetworkAddress(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("legacy network address is missing");
        }
        String normalized = value.trim();
        if (normalized.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            String[] parts = normalized.split("\\.", -1);
            byte[] bytes = new byte[4];
            for (int index = 0; index < parts.length; index++) {
                int part = Integer.parseInt(parts[index]);
                if (part > 255) {
                    throw new IllegalArgumentException("legacy IPv4 component is out of range");
                }
                bytes[index] = (byte) part;
            }
            return new LegacyNetworkAddress(bytes);
        }
        if (!normalized.contains(":") || !normalized.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("legacy network address is not a literal IP address");
        }
        try {
            byte[] bytes = java.net.InetAddress.getByName(normalized).getAddress();
            if (bytes.length != 4 && bytes.length != 16) {
                throw new IllegalArgumentException("legacy IPv6 address has an unsupported binary length");
            }
            return new LegacyNetworkAddress(bytes);
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("legacy IPv6 address is invalid", exception);
        }
    }

    private static Optional<Instant> optionalInstant(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return Optional.of(timestamp.toInstant());
        }
        if (value instanceof Number number) {
            long epochMillis = number.longValue();
            return epochMillis <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(epochMillis));
        }
        throw new IllegalArgumentException("unsupported LiteBans removal timestamp type");
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
            boolean ipBan,
            Optional<Instant> endedAt,
            String ip
    ) {
    }

    private record HistoryRow(long id, String uuid, String username, String ip, Instant observedAt) {
    }
}
