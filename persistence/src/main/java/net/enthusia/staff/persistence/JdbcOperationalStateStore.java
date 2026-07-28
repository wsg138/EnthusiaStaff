package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.ports.OperationalStateStore;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;

public final class JdbcOperationalStateStore implements OperationalStateStore {
    private final DataSource dataSource;

    public JdbcOperationalStateStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public OperationalStateSnapshot current() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT mode, revision, reason, updated_at
                     FROM operational_state
                     WHERE singleton_id = 1
                     """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new ModerationPersistenceException("Operational state singleton is missing");
            }
            return new OperationalStateSnapshot(
                    OperationalMode.valueOf(result.getString("mode")),
                    result.getLong("revision"),
                    result.getString("reason"),
                    result.getTimestamp("updated_at").toInstant()
            );
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read operational state", exception);
        }
    }

    @Override
    public boolean transition(
            long expectedRevision,
            OperationalMode next,
            UUID actorId,
            String reason,
            Instant now
    ) {
        if (expectedRevision < 0 || next == null || reason == null || reason.isBlank() || now == null) {
            throw new IllegalArgumentException("transition fields must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE operational_state
                     SET mode = ?, reason = ?, updated_by = ?, updated_at = ?, revision = revision + 1
                     WHERE singleton_id = 1 AND revision = ?
                     """)) {
            statement.setString(1, next.name());
            statement.setString(2, reason.trim());
            if (actorId == null) {
                statement.setNull(3, java.sql.Types.BINARY);
            } else {
                statement.setBytes(3, UuidBytes.toBytes(actorId));
            }
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setLong(5, expectedRevision);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to transition operational state", exception);
        }
    }

    @Override
    public boolean hasAuthorizedCutover() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT EXISTS(SELECT 1 FROM cutover_records)" );
             ResultSet result = statement.executeQuery()) {
            return result.next() && result.getBoolean(1);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read cutover authorization", exception);
        }
    }
}
