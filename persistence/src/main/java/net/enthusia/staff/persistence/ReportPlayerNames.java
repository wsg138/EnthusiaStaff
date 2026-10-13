package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Current names from the authoritative player directory; no caller-supplied names. */
final class ReportPlayerNames {
    private ReportPlayerNames() { }

    record Pair(String reporter, String target) { }

    static Pair forReport(Connection connection, UUID reportId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT reporter.current_username AS reporter_name,
                       target.current_username AS target_name
                  FROM reports r
                  LEFT JOIN players reporter ON reporter.player_id = r.reporter_id
                  LEFT JOIN players target ON target.player_id = r.target_id
                 WHERE r.report_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(reportId));
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return new Pair("Unknown player", "Unknown player");
                return new Pair(display(rows.getString("reporter_name")),
                        display(rows.getString("target_name")));
            }
        }
    }

    static String display(String currentName) {
        if (currentName == null || !currentName.matches("\\*?[A-Za-z0-9_]{1,32}")) {
            return "Unknown player";
        }
        return currentName;
    }
}
