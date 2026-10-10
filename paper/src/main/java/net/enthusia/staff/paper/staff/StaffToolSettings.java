package net.enthusia.staff.paper.staff;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;

/** Restart-scoped staff-tool runtime settings read from the validated plugin configuration file. */
record StaffToolSettings(
        Set<String> disabledServers,
        Set<String> disabledWorlds,
        Duration randomCooldown,
        Duration targetCooldown,
        Duration toggleCooldown,
        Duration menuCooldown
) {
    private static final long DEFAULT_RANDOM_COOLDOWN_MILLIS = 250L;
    private static final long DEFAULT_TARGET_COOLDOWN_MILLIS = 100L;
    private static final long DEFAULT_TOGGLE_COOLDOWN_MILLIS = 100L;
    private static final long DEFAULT_MENU_COOLDOWN_MILLIS = 100L;

    StaffToolSettings {
        disabledServers = normalize(disabledServers);
        disabledWorlds = normalize(disabledWorlds);
        randomCooldown = checked(randomCooldown, "randomCooldown");
        targetCooldown = checked(targetCooldown, "targetCooldown");
        toggleCooldown = checked(toggleCooldown, "toggleCooldown");
        menuCooldown = checked(menuCooldown, "menuCooldown");
    }

    static StaffToolSettings load(FileConfiguration configuration) {
        java.util.Objects.requireNonNull(configuration, "configuration");
        return new StaffToolSettings(
                Set.copyOf(configuration.getStringList("staff-tools.random-teleport.disabled-servers")),
                Set.copyOf(configuration.getStringList("staff-tools.random-teleport.disabled-worlds")),
                migratedMillis(configuration, "staff-tools.cooldowns.random-teleport-millis",
                        2_000L, DEFAULT_RANDOM_COOLDOWN_MILLIS),
                migratedMillis(configuration, "staff-tools.cooldowns.target-tool-millis",
                        750L, DEFAULT_TARGET_COOLDOWN_MILLIS),
                migratedMillis(configuration, "staff-tools.cooldowns.toggle-tool-millis",
                        500L, DEFAULT_TOGGLE_COOLDOWN_MILLIS),
                migratedMillis(configuration, "staff-tools.cooldowns.menu-millis",
                        500L, DEFAULT_MENU_COOLDOWN_MILLIS)
        );
    }

    Duration cooldownFor(StaffToolDefinition tool) {
        return switch (tool.cooldownClass()) {
            case RANDOM -> randomCooldown;
            case TARGET -> targetCooldown;
            case TOGGLE -> toggleCooldown;
            case MENU -> menuCooldown;
        };
    }

    boolean randomTeleportEnabledOn(String serverId) {
        return serverId != null && !disabledServers.contains(normalize(serverId));
    }

    boolean worldEnabled(String worldName) {
        return worldName != null && !disabledWorlds.contains(normalize(worldName));
    }

    /** Existing generated configs retain old defaults; map only those exact legacy values. */
    private static Duration migratedMillis(FileConfiguration configuration, String path,
            long legacyDefault, long currentDefault) {
        Duration configured = millis(configuration, path, currentDefault);
        return configured.toMillis() == legacyDefault
                ? Duration.ofMillis(currentDefault) : configured;
    }

    private static Duration millis(FileConfiguration configuration, String path, long fallback) {
        long value = configuration.getLong(path, fallback);
        if (value < 0L || value > 60_000L) {
            throw new IllegalArgumentException(path + " must be between 0 and 60000 milliseconds");
        }
        return Duration.ofMillis(value);
    }

    private static Duration checked(Duration duration, String name) {
        java.util.Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException(name + " must be between zero and one minute");
        }
        return duration;
    }

    private static Set<String> normalize(Set<String> values) {
        java.util.Objects.requireNonNull(values, "values");
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(StaffToolSettings::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
