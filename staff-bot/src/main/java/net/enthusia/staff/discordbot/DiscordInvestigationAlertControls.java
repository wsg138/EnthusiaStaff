package net.enthusia.staff.discordbot;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.EvasionAlert;

/** Compact private component identifiers; every action is reauthorized when clicked. */
final class DiscordInvestigationAlertControls {
    private static final String PREFIX = "d09alert:";
    private static final int BASE_PARTS = 2;
    private static final int RESOLVE_PARTS = 3;
    private static final int TARGET_PART = 1;
    private static final int ALERT_PART = 2;
    private static final int MAX_COMPONENT_ID = 100;
    private static final long INVALID_DISCORD_ID = 0L;

    enum Type {
        LINKED,
        HISTORY,
        MODERATE,
        RESOLVE
    }

    record Action(Type type, long targetDiscordId, Optional<UUID> alertId) {
        Action {
            if (type == null || targetDiscordId == INVALID_DISCORD_ID || alertId == null
                    || (type == Type.RESOLVE) != alertId.isPresent()) {
                throw new IllegalArgumentException("linked-alt alert action is invalid");
            }
        }
    }

    private DiscordInvestigationAlertControls() {
    }

    static boolean handles(String customId) {
        return customId != null && customId.startsWith(PREFIX);
    }

    static String linked(EvasionAlert alert) {
        return id(Type.LINKED, alert, false);
    }

    static String history(EvasionAlert alert) {
        return id(Type.HISTORY, alert, false);
    }

    static String moderate(EvasionAlert alert) {
        return id(Type.MODERATE, alert, false);
    }

    static String resolve(EvasionAlert alert) {
        return id(Type.RESOLVE, alert, true);
    }

    static Action parse(String customId) {
        if (!handles(customId)) {
            throw new IllegalArgumentException("not a linked-alt alert action");
        }
        String[] parts = customId.substring(PREFIX.length()).split(":", -1);
        Type type = type(parts);
        int expected = type == Type.RESOLVE ? RESOLVE_PARTS : BASE_PARTS;
        if (parts.length != expected) {
            throw new IllegalArgumentException("linked-alt alert action is malformed");
        }
        long targetId = parseUnsigned(parts[TARGET_PART]);
        Optional<UUID> alertId = type == Type.RESOLVE
                ? Optional.of(uuid(parts[ALERT_PART])) : Optional.empty();
        return new Action(type, targetId, alertId);
    }

    private static Type type(String[] parts) {
        if (parts.length < BASE_PARTS) {
            throw new IllegalArgumentException("linked-alt alert action is malformed");
        }
        return switch (parts[0]) {
            case "linked" -> Type.LINKED;
            case "history" -> Type.HISTORY;
            case "moderate" -> Type.MODERATE;
            case "resolve" -> Type.RESOLVE;
            default -> throw new IllegalArgumentException("unknown linked-alt alert action");
        };
    }

    private static String id(Type type, EvasionAlert alert, boolean includeAlert) {
        if (alert == null) {
            throw new IllegalArgumentException("alert must be present");
        }
        String target = alert.targetDiscordUserId().value();
        String value = PREFIX + type.name().toLowerCase(java.util.Locale.ROOT) + ':' + target
                + (includeAlert ? ":" + alert.alertId() : "");
        if (value.length() > MAX_COMPONENT_ID) {
            throw new IllegalArgumentException("linked-alt alert action exceeds Discord component limit");
        }
        return value;
    }

    private static long parseUnsigned(String value) {
        try {
            long parsed = Long.parseUnsignedLong(value);
            if (parsed == INVALID_DISCORD_ID) {
                throw new IllegalArgumentException("Discord target id must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Discord target id is invalid", exception);
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("linked-alt alert id is invalid", exception);
        }
    }
}
