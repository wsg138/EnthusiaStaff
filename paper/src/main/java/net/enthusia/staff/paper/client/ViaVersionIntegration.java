package net.enthusia.staff.paper.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

final class ViaVersionIntegration {
    private static final int REQUIRED_MAJOR_VERSION = 5;

    private final IntegrationAvailability availability;
    private final String issue;
    private final Object api;
    private final String pluginVersion;
    private final Method playerProtocol;
    private final Method protocolKnown;
    private final Method protocolNumber;
    private final Method protocolName;

    private ViaVersionIntegration(
            IntegrationAvailability availability,
            String issue,
            Object api,
            String pluginVersion,
            Method playerProtocol,
            Method protocolKnown,
            Method protocolNumber,
            Method protocolName
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.api = api;
        this.pluginVersion = pluginVersion;
        this.playerProtocol = playerProtocol;
        this.protocolKnown = protocolKnown;
        this.protocolNumber = protocolNumber;
        this.protocolName = protocolName;
    }

    static ViaVersionIntegration discover(PluginManager plugins) {
        Objects.requireNonNull(plugins, "plugins");
        Plugin plugin = plugins.getPlugin("ViaVersion");
        if (plugin == null || !plugin.isEnabled()) {
            return unavailable(
                    IntegrationAvailability.NOT_INSTALLED,
                    "ViaVersion is not installed or enabled"
            );
        }
        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            Class<?> apiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            Class<?> protocolClass = Class.forName(
                    "com.viaversion.viaversion.api.protocol.version.ProtocolVersion"
            );
            Object api = viaClass.getMethod("getAPI").invoke(null);
            int majorVersion = (int) apiClass.getMethod("majorVersion").invoke(api);
            if (majorVersion != REQUIRED_MAJOR_VERSION) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "ViaVersion API major version " + majorVersion
                                + " is incompatible with required major version "
                                + REQUIRED_MAJOR_VERSION
                );
            }
            String pluginVersion = (String) apiClass.getMethod("getVersion").invoke(api);
            return new ViaVersionIntegration(
                    IntegrationAvailability.AVAILABLE,
                    "",
                    api,
                    pluginVersion,
                    apiClass.getMethod("getPlayerProtocolVersion", UUID.class),
                    protocolClass.getMethod("isKnown"),
                    protocolClass.getMethod("getVersion"),
                    protocolClass.getMethod("getName")
            );
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException | LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "ViaVersion API could not be linked: " + exception.getClass().getSimpleName()
            );
        }
    }

    ProtocolObservation observe(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (availability != IntegrationAvailability.AVAILABLE) {
            return new ProtocolObservation(
                    availability,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    issue
            );
        }
        try {
            Object protocol = playerProtocol.invoke(api, playerId);
            boolean known = protocol != null && (boolean) protocolKnown.invoke(protocol);
            if (!known) {
                return new ProtocolObservation(
                        IntegrationAvailability.UNAVAILABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(pluginVersion),
                        "ViaVersion has no current protocol entry for the player"
                );
            }
            int protocolVersion = (int) protocolNumber.invoke(protocol);
            if (protocolVersion < 0) {
                return new ProtocolObservation(
                        IntegrationAvailability.UNAVAILABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(pluginVersion),
                        "ViaVersion returned an invalid player protocol"
                );
            }
            return new ProtocolObservation(
                    IntegrationAvailability.AVAILABLE,
                    Optional.of(protocolVersion),
                    Optional.of((String) protocolName.invoke(protocol)),
                    Optional.of(pluginVersion),
                    ""
            );
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return new ProtocolObservation(
                    IntegrationAvailability.UNAVAILABLE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(pluginVersion),
                    "ViaVersion player query failed: " + exception.getClass().getSimpleName()
            );
        }
    }

    IntegrationAvailability availability() {
        return availability;
    }

    String issue() {
        return issue;
    }

    private static ViaVersionIntegration unavailable(
            IntegrationAvailability availability,
            String issue
    ) {
        return new ViaVersionIntegration(
                availability,
                issue,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    record ProtocolObservation(
            IntegrationAvailability availability,
            Optional<Integer> protocolVersion,
            Optional<String> minecraftVersion,
            Optional<String> pluginVersion,
            String issue
    ) {
        ProtocolObservation {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(protocolVersion, "protocolVersion");
            Objects.requireNonNull(minecraftVersion, "minecraftVersion");
            Objects.requireNonNull(pluginVersion, "pluginVersion");
            Objects.requireNonNull(issue, "issue");
        }
    }
}
