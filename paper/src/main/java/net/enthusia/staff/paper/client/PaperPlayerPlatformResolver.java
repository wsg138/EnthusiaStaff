package net.enthusia.staff.paper.client;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPlatformDetection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Resolves a player platform from supported runtime provider evidence without inspecting usernames.
 */
public final class PaperPlayerPlatformResolver {
    private final FloodgateIntegration floodgate;
    private final IntegrationAvailability geyser;

    private PaperPlayerPlatformResolver(
            FloodgateIntegration floodgate,
            IntegrationAvailability geyser
    ) {
        this.floodgate = Objects.requireNonNull(floodgate, "floodgate");
        this.geyser = Objects.requireNonNull(geyser, "geyser");
    }

    public static PaperPlayerPlatformResolver discover(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        FloodgateIntegration floodgate = FloodgateIntegration.discover(
                plugin.getServer().getPluginManager()
        );
        Plugin geyserPlugin = plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot");
        if (geyserPlugin == null) {
            geyserPlugin = plugin.getServer().getPluginManager().getPlugin("Geyser");
        }
        IntegrationAvailability geyser = geyserPlugin != null && geyserPlugin.isEnabled()
                ? IntegrationAvailability.AVAILABLE
                : IntegrationAvailability.NOT_INSTALLED;
        return new PaperPlayerPlatformResolver(floodgate, geyser);
    }

    public PlayerPlatform resolve(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        FloodgateIntegration.BedrockObservation observation = floodgate.observe(playerId);
        return PlayerPlatformDetection.resolve(
                observation.availability(),
                observation.floodgatePlayer(),
                geyser
        );
    }
}
