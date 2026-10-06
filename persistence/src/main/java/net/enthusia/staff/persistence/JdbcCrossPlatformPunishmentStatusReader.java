package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;

/** Read-only delivery projection for the Minecraft half of an atomic D08 punishment. */
public final class JdbcCrossPlatformPunishmentStatusReader {
    private final DataSource dataSource;

    public JdbcCrossPlatformPunishmentStatusReader(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    public Optional<Status> find(CaseId caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT state, attempt_count, last_error_code
                     FROM network_outbox
                     WHERE idempotency_key = ?
                     """)) {
            statement.setString(1, "case:" + caseId.value() + ":network-created");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Status(
                        State.valueOf(result.getString("state")),
                        result.getInt("attempt_count"),
                        result.getString("last_error_code")
                ));
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException(
                    "Unable to read cross-platform Minecraft delivery status", exception);
        }
    }

    public record Status(State state, int attempts, String lastErrorCode) {
        public Status {
            if (state == null || attempts < 0) {
                throw new IllegalArgumentException("delivery status is invalid");
            }
        }
    }

    public enum State {
        PENDING,
        LEASED,
        ACKNOWLEDGED,
        DEAD_LETTER
    }
}
