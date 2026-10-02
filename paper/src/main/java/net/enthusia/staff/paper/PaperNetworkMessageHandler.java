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
    private static final String PUNISHMENT_CREATED = "PUNISHMENT_CREATED";
    private static final Set<String> SANCTION_EVENTS = Set.of(
            PUNISHMENT_CREATED,
            "SANCTION_CHANGED",
            "ALT_SANCTION_INHERITED"
    );
    private static final Set<String> FREEZE_EVENTS = Set.of("FREEZE_CHANGED");

    private final ObjectMapper json;
    private final Clock clock;
    private final Consumer<UUID> invalidateSanctionCache;
    private final Function<UUID, Boolean> reconcileFreeze;
    private final Consumer<net.enthusia.staff.domain.network.PunishmentCommitNotification> deliverPunishment;
    private final PaperStaffModeHandoffHandler staffModeHandoff;

    PaperNetworkMessageHandler(ObjectMapper json, Clock clock, Consumer<UUID> invalidateSanctionCache) {
        this(json, clock, invalidateSanctionCache, PaperNetworkMessageHandler::reconcileFreeze);
    }

    PaperNetworkMessageHandler(
            ObjectMapper json,
            Clock clock,
            Consumer<UUID> invalidateSanctionCache,
            Function<UUID, Boolean> reconcileFreeze
    ) {
        this(json, clock, invalidateSanctionCache, reconcileFreeze, ignored -> { });
    }

    PaperNetworkMessageHandler(
            ObjectMapper json, Clock clock, Consumer<UUID> invalidateSanctionCache,
            Function<UUID, Boolean> reconcileFreeze,
            Consumer<net.enthusia.staff.domain.network.PunishmentCommitNotification> deliverPunishment
    ) {
        this(json, clock, invalidateSanctionCache, reconcileFreeze, deliverPunishment, null);
    }

    PaperNetworkMessageHandler(
            ObjectMapper json, Clock clock, Consumer<UUID> invalidateSanctionCache,
            Function<UUID, Boolean> reconcileFreeze,
            Consumer<net.enthusia.staff.domain.network.PunishmentCommitNotification> deliverPunishment,
            PaperStaffModeHandoffHandler staffModeHandoff
    ) {
        this.json = java.util.Objects.requireNonNull(json, "json");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.invalidateSanctionCache = java.util.Objects.requireNonNull(
                invalidateSanctionCache,
                "invalidateSanctionCache"
        );
        this.reconcileFreeze = java.util.Objects.requireNonNull(reconcileFreeze, "reconcileFreeze");
        this.deliverPunishment = java.util.Objects.requireNonNull(deliverPunishment, "deliverPunishment");
        this.staffModeHandoff = staffModeHandoff;
    }

    boolean handle(NetworkOutboxStore inbox, String backendId, ProtocolEnvelope envelope) {
        if (staffModeHandoff != null && staffModeHandoff.handles(envelope)) {
            return staffModeHandoff.handle(envelope);
        }
        var notification = punishmentNotification(envelope);
        UUID sanctionTarget = sanctionTarget(envelope);
        if (sanctionTarget != null) {
            invalidateSanctionCache.accept(sanctionTarget);
        }
        UUID freezeTarget = freezeTarget(envelope);
        if (freezeTarget != null && !reconcileFreeze.apply(freezeTarget)) {
            return false;
        }
        boolean firstDelivery = inbox.recordInboxOnce(
                backendId,
                envelope.messageId(),
                envelope.messageType(),
                "{\"outcome\":\"applied\"}",
                clock.instant()
        );
        if (firstDelivery && notification != null) {
            deliverPunishment.accept(notification);
        }
        return true;
    }

    private net.enthusia.staff.domain.network.PunishmentCommitNotification punishmentNotification(
            ProtocolEnvelope envelope
    ) {
        if (!PUNISHMENT_CREATED.equals(envelope.messageType())) {
            return null;
        }
        try {
            JsonNode payload = punishmentPayload(envelope);
            if (historicalPunishmentPayload(payload)) {
                return null;
            }
            return punishmentNotification(payload, sanctionTypes(payload));
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException exception) {
            throw new IllegalArgumentException("invalid committed punishment notification", exception);
        }
    }

    private JsonNode punishmentPayload(ProtocolEnvelope envelope) throws IOException {
        JsonNode payload = json.readTree(envelope.payloadJson());
        if (payload == null) {
            throw new IllegalArgumentException("empty punishment notification");
        }
        return payload;
    }

    private static boolean historicalPunishmentPayload(JsonNode payload) {
        // Old durable messages continue to invalidate caches without replaying historical online effects.
        return !payload.has("sanctionTypes") && !payload.has("publicReason") && !payload.has("issuedAt");
    }

    private static java.util.List<net.enthusia.staff.domain.sanction.SanctionType> sanctionTypes(JsonNode payload) {
        if (!payload.path("sanctionTypes").isArray()) {
            throw new IllegalArgumentException("invalid sanction types");
        }
        java.util.List<net.enthusia.staff.domain.sanction.SanctionType> types = new java.util.ArrayList<>();
        for (JsonNode type : payload.get("sanctionTypes")) {
            types.add(net.enthusia.staff.domain.sanction.SanctionType.valueOf(type.asText()));
        }
        return types;
    }

    private static net.enthusia.staff.domain.network.PunishmentCommitNotification punishmentNotification(
            JsonNode payload,
            java.util.List<net.enthusia.staff.domain.sanction.SanctionType> types
    ) {
        return new net.enthusia.staff.domain.network.PunishmentCommitNotification(
                new net.enthusia.staff.common.CaseId(payload.path("caseId").asText()),
                UUID.fromString(payload.path("targetId").asText()),
                payload.path("publicReason").asText(),
                java.time.Instant.parse(payload.path("issuedAt").asText()),
                types
        );
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
