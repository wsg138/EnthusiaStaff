package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.StaffPreferenceStore;

public final class JdbcStaffPreferenceStore implements StaffPreferenceStore {
    private final DataSource dataSource;

    public JdbcStaffPreferenceStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Boolean> toolInventoryEnabled(UUID staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException("staffId must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT tool_inventory_enabled
                     FROM staff_preferences
                     WHERE staff_id = ?
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(staffId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(result.getBoolean("tool_inventory_enabled"))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to load staff inventory preference", exception);
        }
    }

    @Override
    public void setToolInventoryEnabled(UUID staffId, boolean enabled, Instant now) {
        if (staffId == null || now == null) {
            throw new IllegalArgumentException("staffId and now must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_preferences(staff_id, tool_inventory_enabled, updated_at)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         tool_inventory_enabled = VALUES(tool_inventory_enabled),
                         updated_at = VALUES(updated_at)
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(staffId));
            statement.setBoolean(2, enabled);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to persist staff inventory preference", exception);
        }
    }
}
