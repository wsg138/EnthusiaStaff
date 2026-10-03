package net.enthusia.staff.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;

/**
 * Cross-server staff transfer snapshot messages exchanged over the persistent backend channel.
 *
 * <ul>
 *   <li>{@value #UPLOAD}: backend -&gt; proxy. The source backend captures its in-memory
 *   vanish/staff-mode state and uploads it <em>before</em> any database write, so the proxy
 *   can proceed with the transfer without waiting on persistence.</li>
 *   <li>Snapshots travel proxy -&gt; destination nested inside the existing
 *   {@code STAFF_MODE_HANDOFF_PREPARE} payload under {@value #PAYLOAD_FIELD}, so no extra
 *   round trip or new message type is needed on that leg.</li>
 * </ul>
 */
public final class TransferSnapshotMessages {
    public static final String UPLOAD = "STAFF_MODE_TRANSFER_SNAPSHOT";
    public static final String PAYLOAD_FIELD = "transferSnapshot";

    private static final ObjectMapper JSON = new ObjectMapper();

    private TransferSnapshotMessages() {
    }

    public static String encode(StaffTransferSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("transfer snapshot is required");
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("playerId", snapshot.playerId().toString());
        payload.put("transferId", snapshot.transferId().toString());
        payload.put("sourceServer", snapshot.sourceServer());
        payload.put("vanished", snapshot.vanished());
        payload.put("staffModeActive", snapshot.staffModeActive());
        if (snapshot.rank() != null) {
            payload.put("rank", snapshot.rank().name());
        }
        if (snapshot.selectedGameMode() != null) {
            payload.put("selectedGameMode", snapshot.selectedGameMode());
        }
        payload.put("capturedAtMillis", snapshot.capturedAtMillis());
        return payload.toString();
    }

    /**
     * Decodes a snapshot, tolerating unknown or missing optional fields so mixed-version
     * backends never break the transfer. Returns {@code null} when the payload is absent.
     */
    public static StaffTransferSnapshot decodeNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            UUID playerId = UUID.fromString(node.path("playerId").asText());
            UUID transferId = UUID.fromString(node.path("transferId").asText());
            String sourceServer = node.path("sourceServer").asText("");
            StaffRank rank = null;
            if (node.hasNonNull("rank")) {
                try {
                    rank = StaffRank.valueOf(node.path("rank").asText());
                } catch (IllegalArgumentException ignored) {
                    // Unknown rank from a newer backend: keep it null and let the
                    // destination resolve the rank from permissions instead.
                }
            }
            String selectedGameMode = node.hasNonNull("selectedGameMode")
                    ? node.path("selectedGameMode").asText(null)
                    : null;
            return new StaffTransferSnapshot(
                    playerId,
                    transferId,
                    sourceServer,
                    node.path("vanished").asBoolean(false),
                    node.path("staffModeActive").asBoolean(false),
                    rank,
                    selectedGameMode,
                    node.path("capturedAtMillis").asLong(0L)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("transfer snapshot payload is invalid", exception);
        }
    }

    public static StaffTransferSnapshot decode(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return decodeNode(JSON.readTree(payloadJson));
        } catch (IOException exception) {
            throw new IllegalArgumentException("transfer snapshot payload is not valid JSON", exception);
        }
    }
}
