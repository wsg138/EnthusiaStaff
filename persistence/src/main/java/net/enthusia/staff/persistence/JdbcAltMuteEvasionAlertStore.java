package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.AltMuteEvasionAlertStore;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Sends only sanctioned, deduplicated metadata. Never stores private chat contents. */
public final class JdbcAltMuteEvasionAlertStore implements AltMuteEvasionAlertStore {
    private final DataSource dataSource;
    private final ObjectMapper json;

    public JdbcAltMuteEvasionAlertStore(DataSource dataSource, ObjectMapper json) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public boolean recordBlockedChat(UUID playerId, ActiveSanction inheritedMute, String serverId, Instant now) {
        if (playerId == null || inheritedMute == null || serverId == null || serverId.isBlank() || now == null) {
            throw new IllegalArgumentException("blocked-alt-chat alert fields are required");
        }
        if (!playerId.equals(inheritedMute.targetId())
                || inheritedMute.inheritedFrom().isEmpty()
                || (inheritedMute.type() != SanctionType.MUTE
                    && inheritedMute.type() != SanctionType.PUBLIC_MUTE)) {
            return false;
        }
        UUID sourceSanction = inheritedMute.inheritedFrom().orElseThrow();
        String key = "alt-muted-chat:" + playerId + ":" + sourceSanction + ":" + now.getEpochSecond() / 3600;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("targetId", playerId.toString());
        payload.put("sourceSanctionId", sourceSanction.toString());
        payload.put("caseId", inheritedMute.caseId().value());
        payload.put("sanctionType", inheritedMute.type().name());
        payload.put("trigger", "BLOCKED_CHAT_ATTEMPT");
        payload.put("serverId", serverId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO discord_outbox(
                         message_id, idempotency_key, destination, event_type,
                         payload_json, available_at, created_at)
                     VALUES (?, ?, 'alerts', 'ALT_MUTED_CHAT_ATTEMPT', ?, ?, ?)
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, key);
            statement.setString(3, json.writeValueAsString(payload));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(now));
            return statement.executeUpdate() == 1;
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode alt mute attempt alert", exception);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record alt mute attempt alert", exception);
        }
    }
}
