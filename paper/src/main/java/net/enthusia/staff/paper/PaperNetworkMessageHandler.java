package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.protocol.ProtocolEnvelope;

final class PaperNetworkMessageHandler {
    private static final Set<String> SANCTION_EVENTS = Set.of(
            "PUNISHMENT_CREATED",
            "SANCTION_CHANGED",
            "ALT_SANCTION_INHERITED"
    );

    private final ObjectMapper json;
    private final Clock clock;
    private final Consumer<UUID> invalidateSanctionCache;

    PaperNetworkMessageHandler(ObjectMapper json, Clock clock, Consumer<UUID> invalidateSanctionCache) {
        this.json = json;
        this.clock = clock;
        this.invalidateSanctionCache = invalidateSanctionCache;
    }

    boolean handle(NetworkOutboxStore inbox, String backendId, ProtocolEnvelope envelope) {
        UUID sanctionTarget = sanctionTarget(envelope);
        if (sanctionTarget != null) {
            invalidateSanctionCache.accept(sanctionTarget);
        }
        inbox.recordInboxOnce(
                backendId,
                envelope.messageId(),
                envelope.messageType(),
                "{\"outcome\":\"applied\"}",
                clock.instant()
        );
        return true;
    }

    private UUID sanctionTarget(ProtocolEnvelope envelope) {
        if (!SANCTION_EVENTS.contains(envelope.messageType())) {
            return null;
        }
        try {
            JsonNode payload = json.readTree(envelope.payloadJson());
            if (payload == null) {
                throw new IllegalArgumentException("Network sanction message has an empty payload");
            }
            return UUID.fromString(payload.path("targetId").asText());
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Network sanction message has an invalid target", exception);
        }
    }
}
