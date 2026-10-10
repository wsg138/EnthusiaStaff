package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
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
    public boolean recordSuspectedChat(UUID playerId, String serverId, Instant now) {
        if (playerId == null || serverId == null || serverId.isBlank() || now == null) {
            throw new IllegalArgumentException("suspected-alt chat fields are required");
        }
        // One indexed pair scan for an already suspected relation; never select chat text or raw IP.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                SELECT CASE WHEN r.lower_player_id = ? THEN r.upper_player_id ELSE r.lower_player_id END
                        AS related_player_id,
                       r.relationship_state, r.confidence,
                       s.sanction_id, s.sanction_type, c.case_id
                FROM alt_relationships r
                JOIN sanctions s ON s.target_id =
                    CASE WHEN r.lower_player_id = ? THEN r.upper_player_id ELSE r.lower_player_id END
                JOIN cases c ON c.case_id = s.case_id
                WHERE (r.lower_player_id = ? OR r.upper_player_id = ?)
                  AND r.confidence < 0.8500
                  AND r.relationship_state NOT IN ('APPROVED_ALT', 'SHARED_HOUSEHOLD', 'NOT_RELATED')
                  AND s.status = 'ACTIVE'
                  AND s.inherited_from IS NULL
                  AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN',
                        'MUTE', 'PUBLIC_MUTE')
                  AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                  AND c.state <> 'FULLY_OVERTURNED'
                ORDER BY r.confidence DESC, s.issued_at DESC
                LIMIT 1
                """)) {
            byte[] id = UuidBytes.toBytes(playerId);
            statement.setBytes(1, id);
            statement.setBytes(2, id);
            statement.setBytes(3, id);
            statement.setBytes(4, id);
            statement.setTimestamp(5, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                UUID related = UuidBytes.fromBytes(result.getBytes("related_player_id"));
                UUID sanction = UuidBytes.fromBytes(result.getBytes("sanction_id"));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("targetId", playerId.toString());
                payload.put("relatedPlayerId", related.toString());
                payload.put("sanctionId", sanction.toString());
                payload.put("caseId", result.getString("case_id"));
                payload.put("sanctionType", result.getString("sanction_type"));
                payload.put("relationshipState", result.getString("relationship_state"));
                payload.put("confidencePolicyGrade", result.getBigDecimal("confidence"));
                payload.put("trigger", "SUSPECTED_ALT_CHAT");
                payload.put("serverId", serverId);
                String key = "suspected-alt-chat:" + playerId + ":" + sanction + ":"
                        + now.getEpochSecond() / 3600;
                try (PreparedStatement outbox = connection.prepareStatement("""
                        INSERT IGNORE INTO discord_outbox(message_id, idempotency_key, destination,
                            event_type, payload_json, available_at, created_at)
                        VALUES (?, ?, 'alerts', 'ALT_SUSPECTED_CHAT', ?, ?, ?)
                        """)) {
                    outbox.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                    outbox.setString(2, key);
                    outbox.setString(3, json.writeValueAsString(payload));
                    outbox.setTimestamp(4, Timestamp.from(now));
                    outbox.setTimestamp(5, Timestamp.from(now));
                    return outbox.executeUpdate() == 1;
                }
            }
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode suspected-alt chat alert", exception);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to record suspected-alt chat alert", exception);
        }
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
