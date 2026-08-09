package net.enthusia.staff.paper.tester;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CheatTesterEvidence {
    private static final int MAX_EVIDENCE_CHARS = 32 * 1024;
    private static final int MAX_REASON_CHARS = 255;

    private final Clock clock;
    private final CheatTesterSettings settings;
    private final ObjectMapper json = new ObjectMapper();

    CheatTesterEvidence(Clock clock, CheatTesterSettings settings) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    String capture(Player target, CheatTesterSession session, String reason) {
        Map<String, Object> values = base(session, reason);
        switch (session.type) {
            case TOTEM_REFILL -> addTotem(values, target);
            case AUTO_ARMOR -> addArmor(values, target, session);
            case VELOCITY -> values.put("displacement", displacement(target.getLocation(), session.startPoint));
            case NO_FALL -> addNoFall(values, target, session);
            case FAKE_ENTITY -> addFake(values, session);
            default -> throw new IllegalStateException("Unsupported cheat tester type: " + session.type);
        }
        return serialize(values);
    }

    String withoutTarget(CheatTesterSession session, String reason) {
        Map<String, Object> values = base(session, reason);
        addFake(values, session);
        values.put("targetOnlineAtFinish", false);
        return serialize(values);
    }

    String withoutProbe(CheatTesterSession session, String reason) {
        Map<String, Object> values = base(session, reason);
        values.put("probeStarted", false);
        if (session.type == CheatTesterType.FAKE_ENTITY) {
            addFake(values, session);
        }
        return serialize(values);
    }

    String configuration(CheatTesterSession session) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("schemaVersion", 1);
        configuration.put("tester", session.type.id());
        configuration.put("timeoutMillis", settings.sessionTimeout().toMillis());
        configuration.put("probeTicks", settings.probeTicks());
        if (session.fakeHandle != null) {
            configuration.put("fakeEntityId", session.fakeHandle.entityId());
            configuration.put("fakeEntityUuid", session.fakeHandle.entityUuid().toString());
        }
        return serialize(configuration);
    }

    String summary(CheatTesterSession session, String encodedEvidence) {
        try {
            JsonNode node = json.readTree(encodedEvidence);
            return switch (session.type) {
                case TOTEM_REFILL -> "offhand refill observed=" + node.path("offhandTotemObserved").asBoolean(false);
                case AUTO_ARMOR -> "armor re-equip observed=" + node.path("armorReequippedObserved").asBoolean(false);
                case VELOCITY -> "displacement=" + decimal(node.path("displacement").asDouble(), 2);
                case NO_FALL -> "airborne resets=" + node.path("airborneFallResets").asInt()
                        + ", max fall distance=" + decimal(node.path("maximumFallDistance").asDouble(), 2);
                case FAKE_ENTITY -> "interactions=" + node.path("interactions").asInt()
                        + ", attacks=" + node.path("attacks").asInt()
                        + ", min aim angle=" + decimal(node.path("minimumAimAngleDegrees").asDouble(180.0D), 1) + "°";
            };
        } catch (JsonProcessingException exception) {
            return session.type.displayName() + " evidence saved";
        }
    }

    private Map<String, Object> base(CheatTesterSession session, String reason) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tester", session.type.id());
        values.put("sessionId", session.sessionId.toString());
        values.put("durationMillis", Math.max(0L, clock.instant().toEpochMilli() - session.startedAt.toEpochMilli()));
        values.put("termination", boundedReason(reason));
        values.put("automaticPunishment", false);
        return values;
    }

    private static void addTotem(Map<String, Object> values, Player target) {
        values.put("offhandTotemObserved",
                target.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING);
    }

    private static void addArmor(Map<String, Object> values, Player target, CheatTesterSession session) {
        ItemStack[] armor = target.getInventory().getArmorContents();
        int slot = session.probe.armorSlot();
        boolean equipped = slot >= 0 && slot < armor.length
                && armor[slot] != null && !armor[slot].isEmpty();
        values.put("armorReequippedObserved", equipped);
    }

    private static void addNoFall(Map<String, Object> values, Player target, CheatTesterSession session) {
        values.put("maximumFallDistance", session.maxFallDistance);
        values.put("airborneFallResets", session.airborneFallResets.get());
        values.put("displacement", displacement(target.getLocation(), session.startPoint));
    }

    private static void addFake(Map<String, Object> values, CheatTesterSession session) {
        values.put("interactions", session.fakeInteractions.get());
        values.put("attacks", session.fakeAttacks.get());
        values.put("firstInteractionMillis", session.firstInteractionMillis.get());
        values.put("minimumAimAngleDegrees", session.minimumAimAngleDegrees);
    }

    private String serialize(Map<String, Object> values) {
        try {
            String serialized = json.writeValueAsString(values);
            if (serialized.length() > MAX_EVIDENCE_CHARS) {
                throw new IllegalArgumentException("tester evidence exceeded safety limit");
            }
            return serialized;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tester evidence", exception);
        }
    }

    static String boundedReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified tester termination";
        }
        return reason.length() <= MAX_REASON_CHARS ? reason : reason.substring(0, MAX_REASON_CHARS);
    }

    static double displacement(Location current, CheatTesterSession.StartPoint start) {
        if (start == null || current == null || current.getWorld() == null
                || !current.getWorld().getUID().equals(start.worldId())) {
            return -1.0D;
        }
        double dx = current.getX() - start.x();
        double dy = current.getY() - start.y();
        double dz = current.getZ() - start.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String decimal(double value, int places) {
        return String.format(Locale.ROOT, "% ." + places + "f", value).trim();
    }
}
