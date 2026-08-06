package net.enthusia.staff.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPlatformDetection;
import org.slf4j.Logger;

/**
 * Uses Floodgate's supported public API from its own plugin class loader so the API is not shaded.
 */
final class VelocityPlayerPlatformResolver {
    private final FloodgateProbe floodgate;
    private final IntegrationAvailability geyser;
    private final Consumer<String> warning;
    private final AtomicBoolean queryFailureReported = new AtomicBoolean();

    VelocityPlayerPlatformResolver(
            FloodgateProbe floodgate,
            IntegrationAvailability geyser,
            Consumer<String> warning
    ) {
        this.floodgate = Objects.requireNonNull(floodgate, "floodgate");
        this.geyser = Objects.requireNonNull(geyser, "geyser");
        this.warning = Objects.requireNonNull(warning, "warning");
    }

    static VelocityPlayerPlatformResolver discover(ProxyServer proxy, Logger logger) {
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(logger, "logger");
        FloodgateProbe probe = ReflectiveFloodgateProbe.discover(
                proxy.getPluginManager().getPlugin("floodgate")
        );
        IntegrationAvailability geyser = proxy.getPluginManager().getPlugin("geyser").isPresent()
                || proxy.getPluginManager().getPlugin("geyser-velocity").isPresent()
                ? IntegrationAvailability.AVAILABLE
                : IntegrationAvailability.NOT_INSTALLED;
        if (geyser == IntegrationAvailability.AVAILABLE
                && probe.availability() != IntegrationAvailability.AVAILABLE) {
            logger.warn(
                    "Geyser is present but Floodgate identity evidence is {}; platform writes will remain UNKNOWN",
                    probe.availability()
            );
        }
        return new VelocityPlayerPlatformResolver(
                probe,
                geyser,
                message -> logger.warn("{}", message)
        );
    }

    PlayerPlatform resolve(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (floodgate.availability() != IntegrationAvailability.AVAILABLE) {
            return PlayerPlatformDetection.resolve(floodgate.availability(), false, geyser);
        }
        try {
            return PlayerPlatformDetection.resolve(
                    IntegrationAvailability.AVAILABLE,
                    floodgate.isFloodgatePlayer(playerId),
                    geyser
            );
        } catch (RuntimeException exception) {
            if (queryFailureReported.compareAndSet(false, true)) {
                warning.accept("Floodgate player identity query failed; platform writes will remain UNKNOWN until restart");
            }
            return PlayerPlatform.UNKNOWN;
        }
    }

    interface FloodgateProbe {
        IntegrationAvailability availability();

        boolean isFloodgatePlayer(UUID playerId);
    }

    private static final class ReflectiveFloodgateProbe implements FloodgateProbe {
        private final IntegrationAvailability availability;
        private final Object api;
        private final Method isFloodgatePlayer;

        private ReflectiveFloodgateProbe(
                IntegrationAvailability availability,
                Object api,
                Method isFloodgatePlayer
        ) {
            this.availability = Objects.requireNonNull(availability, "availability");
            this.api = api;
            this.isFloodgatePlayer = isFloodgatePlayer;
        }

        static FloodgateProbe discover(Optional<PluginContainer> container) {
            if (container.isEmpty()) {
                return unavailable(IntegrationAvailability.NOT_INSTALLED);
            }
            Object plugin = container.orElseThrow().getInstance().orElse(null);
            if (plugin == null) {
                return unavailable(IntegrationAvailability.UNAVAILABLE);
            }
            try {
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> apiClass = Class.forName(
                        "org.geysermc.floodgate.api.FloodgateApi",
                        true,
                        loader
                );
                Object api = apiClass.getMethod("getInstance").invoke(null);
                if (api == null) {
                    return unavailable(IntegrationAvailability.UNAVAILABLE);
                }
                return new ReflectiveFloodgateProbe(
                        IntegrationAvailability.AVAILABLE,
                        api,
                        apiClass.getMethod("isFloodgatePlayer", UUID.class)
                );
            } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException | LinkageError | RuntimeException exception) {
                return unavailable(IntegrationAvailability.INCOMPATIBLE);
            }
        }

        private static FloodgateProbe unavailable(IntegrationAvailability availability) {
            return new ReflectiveFloodgateProbe(availability, null, null);
        }

        @Override
        public IntegrationAvailability availability() {
            return availability;
        }

        @Override
        public boolean isFloodgatePlayer(UUID playerId) {
            if (availability != IntegrationAvailability.AVAILABLE) {
                throw new IllegalStateException("Floodgate identity API is unavailable");
            }
            try {
                return (boolean) isFloodgatePlayer.invoke(api, playerId);
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
                throw new IllegalStateException("Floodgate identity query failed", exception);
            }
        }
    }
}
