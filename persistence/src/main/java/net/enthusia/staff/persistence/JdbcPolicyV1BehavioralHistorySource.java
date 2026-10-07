package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1BehavioralHistorySource;

/**
 * Completeness-safe read-only view of factual Policy v1 cases for Policy v2
 * history compatibility. This intentionally reads case rows directly instead
 * of the paginated human-facing moderation history API.
 */
public final class JdbcPolicyV1BehavioralHistorySource implements PolicyV1BehavioralHistorySource {
    private static final String COMPLETE_HISTORY_SQL = """
            SELECT c.case_id, c.issued_at, c.exact_reason_id
            FROM cases c
            JOIN punishment_steps p ON p.case_id = c.case_id
            WHERE c.target_id = ?
              AND c.issued_at <= ?
              AND c.state <> 'FULLY_OVERTURNED'
              AND p.escalation_contributes = TRUE
            ORDER BY c.issued_at ASC, c.case_id ASC
            """;

    private final DataSource dataSource;

    public JdbcPolicyV1BehavioralHistorySource(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("Policy v1 history data source must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<LegacyFinding> completeHistory(UUID subjectId, Instant asOf) {
        if (subjectId == null || asOf == null) {
            throw new IllegalArgumentException("Policy v1 complete history request is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COMPLETE_HISTORY_SQL)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId));
            statement.setTimestamp(2, Timestamp.from(asOf));
            try (ResultSet result = statement.executeQuery()) {
                List<LegacyFinding> findings = new ArrayList<>();
                while (result.next()) {
                    findings.add(new LegacyFinding(
                            result.getString("case_id"),
                            result.getTimestamp("issued_at").toInstant(),
                            result.getString("exact_reason_id")
                    ));
                }
                return List.copyOf(findings);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read complete Policy v1 behavioral history", exception);
        }
    }
}
