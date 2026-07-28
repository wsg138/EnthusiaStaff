package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.evidence.AutoClickerHandshakeEvidence;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;

final class ClientEvidencePersistence {
    private ClientEvidencePersistence() {
    }

    static UUID insert(
            Connection connection,
            ObjectMapper json,
            ClientEvidenceSnapshot snapshot
    ) throws SQLException, JsonProcessingException {
        UUID snapshotId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO client_evidence_snapshots(snapshot_id, player_id, captured_at,
                    platform, protocol_version, reported_brand, evidence_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(snapshotId));
            statement.setBytes(2, UuidBytes.toBytes(snapshot.playerId()));
            statement.setTimestamp(3, Timestamp.from(snapshot.capturedAt()));
            statement.setString(4, snapshot.platform().name());
            if (snapshot.protocolVersion().isPresent()) {
                statement.setInt(5, snapshot.protocolVersion().orElseThrow());
            } else {
                statement.setNull(5, Types.INTEGER);
            }
            optionalString(statement, 6, snapshot.reportedBrand().orElse(null));
            statement.setString(7, json.writeValueAsString(toJson(snapshot)));
            statement.executeUpdate();
        }
        return snapshotId;
    }

    static Map<String, Object> toJson(ClientEvidenceSnapshot snapshot) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("playerId", snapshot.playerId().toString());
        evidence.put("capturedAt", snapshot.capturedAt().toString());
        evidence.put("platform", snapshot.platform().name());
        evidence.put("protocolVersion", snapshot.protocolVersion().orElse(null));
        evidence.put("minecraftVersion", snapshot.minecraftVersion().orElse(null));
        evidence.put("reportedBrand", snapshot.reportedBrand().orElse(null));
        evidence.put("viaVersionStatus", snapshot.viaVersion().name());
        evidence.put("viaVersionPluginVersion", snapshot.viaVersionPluginVersion().orElse(null));
        evidence.put("floodgateStatus", snapshot.floodgate().name());
        evidence.put("floodgatePlayer", snapshot.floodgatePlayer());
        evidence.put("bedrockVersion", snapshot.bedrockVersion().orElse(null));
        evidence.put("bedrockDevice", snapshot.bedrockDevice().orElse(null));
        evidence.put("geyserStatus", snapshot.geyser().name());
        evidence.put("autoClickerStatus", snapshot.autoClicker().name());
        evidence.put("autoClickerHandshake", snapshot.autoClickerHandshake()
                .map(ClientEvidencePersistence::autoClickerJson)
                .orElse(null));
        evidence.put("polarStatus", snapshot.polar().name());
        evidence.put("polarMetadata", snapshot.polarMetadata().orElse(null));
        return java.util.Collections.unmodifiableMap(evidence);
    }

    private static Map<String, Object> autoClickerJson(AutoClickerHandshakeEvidence evidence) {
        return Map.of(
                "modVersion", evidence.modVersion(),
                "loader", evidence.loader(),
                "minecraftVersion", evidence.minecraftVersion(),
                "receivedAt", evidence.receivedAt().toString()
        );
    }

    private static void optionalString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
