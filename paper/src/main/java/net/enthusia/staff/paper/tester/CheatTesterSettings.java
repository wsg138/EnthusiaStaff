package net.enthusia.staff.paper.tester;

import java.time.Duration;
import org.bukkit.configuration.ConfigurationSection;

public record CheatTesterSettings(
        Duration sessionTimeout,
        int maximumActiveGlobal,
        int maximumActivePerStaff,
        double fakeEntityDistance,
        double velocityHorizontal,
        double velocityVertical,
        double noFallVertical,
        int probeTicks
) {
    private static final long MIN_TIMEOUT_MILLIS = 1_000L;
    private static final long MAX_TIMEOUT_MILLIS = 15_000L;

    public CheatTesterSettings {
        if (sessionTimeout == null
                || sessionTimeout.toMillis() < MIN_TIMEOUT_MILLIS
                || sessionTimeout.toMillis() > MAX_TIMEOUT_MILLIS
                || maximumActiveGlobal < 1 || maximumActiveGlobal > 32
                || maximumActivePerStaff < 1 || maximumActivePerStaff > maximumActiveGlobal
                || fakeEntityDistance < 1.0 || fakeEntityDistance > 8.0
                || velocityHorizontal < 0.0 || velocityHorizontal > 2.0
                || velocityVertical < 0.0 || velocityVertical > 2.0
                || noFallVertical < 0.1 || noFallVertical > 2.0
                || probeTicks < 10 || probeTicks > 300) {
            throw new IllegalArgumentException("cheat tester settings are outside safe bounds");
        }
    }

    public static CheatTesterSettings load(ConfigurationSection root) {
        if (root == null) {
            return defaults();
        }
        return new CheatTesterSettings(
                Duration.ofMillis(root.getLong("timeout-millis", 4_000L)),
                root.getInt("maximum-active-global", 8),
                root.getInt("maximum-active-per-staff", 2),
                root.getDouble("fake-entity-distance", 3.0D),
                root.getDouble("velocity.horizontal", 0.75D),
                root.getDouble("velocity.vertical", 0.30D),
                root.getDouble("no-fall.vertical", 0.70D),
                root.getInt("probe-ticks", 60)
        );
    }

    public static CheatTesterSettings defaults() {
        return new CheatTesterSettings(
                Duration.ofSeconds(4),
                8,
                2,
                3.0D,
                0.75D,
                0.30D,
                0.70D,
                60
        );
    }
}
