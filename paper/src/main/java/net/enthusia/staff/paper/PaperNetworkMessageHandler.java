package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.paper.freeze.FreezeNetworkReconciler;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.bukkit.Bukkit;

final class PaperNetworkMessageHandler {
    private static final Set<String> SANCTION_EVENTS = Set.of(
            "PUNISHMENT_CREATED",
            "SANCTION_CHANGED",
            "ALT_SANCTION_INHERITED"
    );
    private static final Set<String> FREEZE_EVENTS = Set.of("FREEZE_CHANGED");

    private final ObjectMapper json;
    private final Clock clock;
    private final Consumer<UUID> invalidateSanctionCache;
    private final Function<UUID, Boolean> reconcileFreeze;

    PaperNetworkMessageHandler(ObjectMapper json, Clock clock, Consumer<UUID> invalidateSanctionCache) {
        this(json, clock, invalidateSanctionCache, PaperNetworkMessageHandler::reconcileFreeze);
    }

    PaperNetworkMessageHandler(
            ObjectMapper json,
            Clock clock,
            Consumer<UUID> invalidateSanctionCache,
            Function<UUID, Boolean> reconcileFreeze
    ) {
        this.json = java.util.Objects.requireNonNull(json, "json");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.invalidateSanctionCache = java.util.Objects.requireNonNull(
                invalidateSanctionCache,
                "invalidateSanctionCache"
        );
        this.reconcileFreeze = java.util.Objects.requireNonNull(reconcileFreeze, "reconcileFreeze");
    }

    boolean handle(NetworkOutboxStore inbox, String backendId, ProtocolEnvelope envelope) {
        UUID sanctionTarget = sanctionTarget(envelope);
        if (sanctionTarget != null) {
            invalidateSanctionCache.accept(sanctionTarget);
        }
        UUID freezeTarget = freezeTarget(envelope);
        if (freezeTarget != null && !reconcileFreeze.apply(freezeTarget)) {
            return false;
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
        return target(envelope, SANCTION_EVENTS, "sanction");
    }

    private UUID freezeTarget(ProtocolEnvelope envelope) {
        return target(envelope, FREEZE_EVENTS, "freeze");
    }

    private UUID target(ProtocolEnvelope envelope, Set<String> messageTypes, String label) {
        if (!messageTypes.contains(envelope.messageType())) {
            return null;
        }
        try {
            JsonNode payload = json.readTree(envelope.payloadJson());
            if (payload == null) {
                throw new IllegalArgumentException("Network " + label + " message has an empty payload");
            }
            return UUID.fromString(payload.path("targetId").asText());
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Network " + label + " message has an invalid target",
                    exception
            );
        }
    }

    private static boolean reconcileFreeze(UUID playerId) {
        FreezeNetworkReconciler reconciler = Bukkit.getServicesManager().load(FreezeNetworkReconciler.class);
        return reconciler != null && reconciler.reconcile(playerId);
    }
}
