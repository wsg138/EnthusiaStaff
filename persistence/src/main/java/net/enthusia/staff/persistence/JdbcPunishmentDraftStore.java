package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PunishmentDraftStore;

public final class JdbcPunishmentDraftStore implements PunishmentDraftStore {
    private static final String COLUMNS = """
            draft_id, actor_id, target_id, reason_id, internal_explanation, visibility,
            command_name, configuration_version, step_ordinal, step_label, sanctions_json,
            created_at, expires_at
            """;

    private final DataSource dataSource;
    private final PunishmentDraftSanctionCodec sanctions;

    public JdbcPunishmentDraftStore(DataSource dataSource, ObjectMapper json) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
        this.sanctions = new PunishmentDraftSanctionCodec(json);
    }

    @Override
    public void save(PunishmentDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must be present");
        }
        deleteExpired(draft.createdAt());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO punishment_drafts(
                         draft_id, actor_id, target_id, reason_id, internal_explanation, visibility,
                         command_name, configuration_version, step_ordinal, step_label, sanctions_json,
                         created_at, updated_at, expires_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         draft_id = VALUES(draft_id), reason_id = VALUES(reason_id),
                         internal_explanation = VALUES(internal_explanation), visibility = VALUES(visibility),
                         command_name = VALUES(command_name), configuration_version = VALUES(configuration_version),
                         step_ordinal = VALUES(step_ordinal), step_label = VALUES(step_label),
                         sanctions_json = VALUES(sanctions_json), created_at = VALUES(created_at),
                         updated_at = VALUES(updated_at), expires_at = VALUES(expires_at)
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(draft.draftId()));
            statement.setBytes(2, UuidBytes.toBytes(draft.actorId()));
            statement.setBytes(3, UuidBytes.toBytes(draft.targetId()));
            statement.setString(4, draft.reasonId());
            statement.setString(5, draft.internalExplanation());
            statement.setString(6, draft.visibility().name());
            statement.setString(7, draft.commandName());
            statement.setString(8, draft.expectation().configurationVersion());
            statement.setInt(9, draft.expectation().stepOrdinal());
            statement.setString(10, draft.expectation().stepLabel());
            statement.setString(11, sanctions.encode(draft.expectation().sanctions()));
            statement.setTimestamp(12, Timestamp.from(draft.createdAt()));
            statement.setTimestamp(13, Timestamp.from(draft.createdAt()));
            statement.setTimestamp(14, Timestamp.from(draft.expiresAt()));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to save punishment draft", exception);
        }
    }

    @Override
    public Optional<PunishmentDraft> find(UUID draftId, UUID actorId, Instant now) {
        validateLookup(draftId, actorId, now);
        String sql = "SELECT " + COLUMNS + " FROM punishment_drafts "
                + "WHERE draft_id = ? AND actor_id = ? AND expires_at > ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(draftId));
            statement.setBytes(2, UuidBytes.toBytes(actorId));
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to load punishment draft", exception);
        }
    }

    @Override
    public Optional<PunishmentDraft> findLatest(UUID actorId, UUID targetId, Instant now) {
        validateLookup(actorId, targetId, now);
        String sql = "SELECT " + COLUMNS + " FROM punishment_drafts "
                + "WHERE actor_id = ? AND target_id = ? AND expires_at > ? "
                + "ORDER BY updated_at DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(actorId));
            statement.setBytes(2, UuidBytes.toBytes(targetId));
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to resume punishment draft", exception);
        }
    }

    @Override
    public boolean delete(UUID draftId, UUID actorId) {
        if (draftId == null || actorId == null) {
            throw new IllegalArgumentException("draft and actor identifiers must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM punishment_drafts WHERE draft_id = ? AND actor_id = ?"
             )) {
            statement.setBytes(1, UuidBytes.toBytes(draftId));
            statement.setBytes(2, UuidBytes.toBytes(actorId));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to delete punishment draft", exception);
        }
    }

    @Override
    public int deleteExpired(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM punishment_drafts WHERE expires_at <= ?"
             )) {
            statement.setTimestamp(1, Timestamp.from(now));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to prune punishment drafts", exception);
        }
    }

    private PunishmentDraft read(ResultSet result) throws SQLException {
        try {
            PunishmentExpectation expectation = new PunishmentExpectation(
                    result.getString("configuration_version"),
                    result.getInt("step_ordinal"),
                    result.getString("step_label"),
                    sanctions.decode(result.getString("sanctions_json"))
            );
            return new PunishmentDraft(
                    UuidBytes.fromBytes(result.getBytes("draft_id")),
                    UuidBytes.fromBytes(result.getBytes("actor_id")),
                    UuidBytes.fromBytes(result.getBytes("target_id")),
                    result.getString("reason_id"),
                    result.getString("internal_explanation"),
                    CaseVisibility.valueOf(result.getString("visibility")),
                    result.getString("command_name"),
                    expectation,
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("expires_at").toInstant()
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SQLException("Stored punishment draft is invalid", exception);
        }
    }

    private static void validateLookup(UUID first, UUID second, Instant now) {
        if (first == null || second == null || now == null) {
            throw new IllegalArgumentException("draft lookup fields must be present");
        }
    }
}
