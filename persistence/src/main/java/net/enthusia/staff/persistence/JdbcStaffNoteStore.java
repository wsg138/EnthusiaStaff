package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.StaffNoteStore;

/** Read-only JDBC view over the existing staff_notes table. */
public final class JdbcStaffNoteStore implements StaffNoteStore {
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final DataSource dataSource;

    public JdbcStaffNoteStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<StaffNote> recent(UUID targetId, int limit) {
        if (targetId == null || limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("target and a limit from 1 to 100 are required");
        }
        String sql = """
                SELECT note_id, target_id, actor_id, note_text, created_at
                FROM staff_notes
                WHERE target_id = ?
                ORDER BY created_at DESC, note_id DESC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<StaffNote> notes = new ArrayList<>();
                while (result.next()) {
                    notes.add(new StaffNote(
                            UuidBytes.fromBytes(result.getBytes("note_id")),
                            UuidBytes.fromBytes(result.getBytes("target_id")),
                            UuidBytes.fromBytes(result.getBytes("actor_id")),
                            result.getString("note_text"),
                            result.getTimestamp("created_at").toInstant()
                    ));
                }
                return List.copyOf(notes);
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read staff notes", exception);
        }
    }
}
