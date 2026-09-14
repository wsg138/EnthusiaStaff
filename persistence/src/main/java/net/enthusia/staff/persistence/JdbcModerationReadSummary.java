package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/** Narrow read-only structured summaries used by the staging moderation bridge. */
final class JdbcModerationReadSummary {
    private static final String RELEVANT_CASE_COUNTS = """
            SELECT c.sanction_family, COUNT(*) AS relevant_count
            FROM cases c
            JOIN punishment_steps ps ON ps.case_id = c.case_id
            WHERE c.target_id = ?
              AND c.state <> 'FULLY_OVERTURNED'
              AND ps.escalation_contributes = TRUE
              AND c.sanction_family IN ('spam', 'harassment', 'hate', 'advertising', 'cheating')
            GROUP BY c.sanction_family
            """;

    private JdbcModerationReadSummary() {
    }

    static Map<String, Long> relevantCaseCounts(DataSource dataSource, UUID targetId) {
        if (dataSource == null || targetId == null) {
            throw new IllegalArgumentException("summary read dependencies must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(RELEVANT_CASE_COUNTS)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            return readCounts(statement);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read moderation history summary", exception);
        }
    }

    private static Map<String, Long> readCounts(PreparedStatement statement) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String family = result.getString("sanction_family");
                long count = result.getLong("relevant_count");
                if (family != null && !family.isBlank() && count > 0) {
                    counts.put(family, count);
                }
            }
        }
        return Map.copyOf(counts);
    }
}
