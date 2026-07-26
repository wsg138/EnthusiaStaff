package net.enthusia.staff.paper.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

final class FloodgateIntegration {
    private final IntegrationAvailability availability;
    private final String issue;
    private final Object api;
    private final Method isFloodgatePlayer;
    private final Method getPlayer;
    private final Method getVersion;
    private final Method getDeviceOs;

    private FloodgateIntegration(
            IntegrationAvailability availability,
            String issue,
            Object api,
            Method isFloodgatePlayer,
            Method getPlayer,
            Method getVersion,
            Method getDeviceOs
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.api = api;
        this.isFloodgatePlayer = isFloodgatePlayer;
        this.getPlayer = getPlayer;
        this.getVersion = getVersion;
        this.getDeviceOs = getDeviceOs;
    }

    static FloodgateIntegration discover(PluginManager plugins) {
        Objects.requireNonNull(plugins, "plugins");
        Plugin plugin = plugins.getPlugin("floodgate");
        if (plugin == null || !plugin.isEnabled()) {
            return unavailable(
                    IntegrationAvailability.NOT_INSTALLED,
                    "Floodgate is not installed or enabled"
            );
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Class<?> playerClass = Class.forName(
                    "org.geysermc.floodgate.api.player.FloodgatePlayer"
            );
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return new FloodgateIntegration(
                    IntegrationAvailability.AVAILABLE,
                    "",
                    api,
                    apiClass.getMethod("isFloodgatePlayer", UUID.class),
                    apiClass.getMethod("getPlayer", UUID.class),
                    playerClass.getMethod("getVersion"),
                    playerClass.getMethod("getDeviceOs")
            );
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException | LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "Floodgate API could not be linked: " + exception.getClass().getSimpleName()
            );
        }
    }

    BedrockObservation observe(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (availability != IntegrationAvailability.AVAILABLE) {
            return new BedrockObservation(
                    availability,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    issue
            );
        }
        try {
            boolean bedrock = (boolean) isFloodgatePlayer.invoke(api, playerId);
            if (!bedrock) {
                return new BedrockObservation(
                        IntegrationAvailability.AVAILABLE,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        ""
                );
            }
            Object player = getPlayer.invoke(api, playerId);
            if (player == null) {
                return new BedrockObservation(
                        IntegrationAvailability.UNAVAILABLE,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        "Floodgate identified a Bedrock player but returned no player metadata"
                );
            }
            Object device = getDeviceOs.invoke(player);
            return new BedrockObservation(
                    IntegrationAvailability.AVAILABLE,
                    true,
                    optionalText((String) getVersion.invoke(player)),
                    optionalText(device == null ? null : device.toString()),
                    ""
            );
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return new BedrockObservation(
                    IntegrationAvailability.UNAVAILABLE,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    "Floodgate player query failed: " + exception.getClass().getSimpleName()
            );
        }
    }

    IntegrationAvailability availability() {
        return availability;
    }

    String issue() {
        return issue;
    }

    private static FloodgateIntegration unavailable(
            IntegrationAvailability availability,
            String issue
    ) {
        return new FloodgateIntegration(availability, issue, null, null, null, null, null);
    }

    private static Optional<String> optionalText(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    record BedrockObservation(
            IntegrationAvailability availability,
            boolean floodgatePlayer,
            Optional<String> bedrockVersion,
            Optional<String> bedrockDevice,
            String issue
    ) {
        BedrockObservation {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(bedrockVersion, "bedrockVersion");
            Objects.requireNonNull(bedrockDevice, "bedrockDevice");
            Objects.requireNonNull(issue, "issue");
        }
    }
}
