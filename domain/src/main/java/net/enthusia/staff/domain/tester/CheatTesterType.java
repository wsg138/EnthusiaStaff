package net.enthusia.staff.domain.tester;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Release-safe evidence-only cheat tester types. */
public enum CheatTesterType {
    TOTEM_REFILL("totem-refill", "Totem refill"),
    NO_FALL("no-fall", "No-fall"),
    VELOCITY("velocity", "Velocity / anti-knockback"),
    AUTO_ARMOR("auto-armor", "Auto-armor"),
    FAKE_ENTITY("fake-entity", "Fake entity");

    private final String id;
    private final String displayName;

    CheatTesterType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean mutatesTargetState() {
        return this != FAKE_ENTITY;
    }

    public static Optional<CheatTesterType> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values()).filter(type -> type.id.equals(normalized)).findFirst();
    }
}
