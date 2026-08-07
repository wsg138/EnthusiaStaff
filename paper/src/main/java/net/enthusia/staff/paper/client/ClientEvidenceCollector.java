package net.enthusia.staff.paper.client;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPlatformDetection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientEvidenceCollector {
    private final Clock clock;
    private final ViaVersionIntegration viaVersion;
    private final FloodgateIntegration floodgate;
    private final IntegrationAvailability geyser;
    private final String geyserIssue;
    private final AutoClickerIntegration autoClicker;
    private final IntegrationAvailability polar;
    private final String polarIssue;

    private ClientEvidenceCollector(
            Clock clock,
            ViaVersionIntegration viaVersion,
            FloodgateIntegration floodgate,
            IntegrationAvailability geyser,
            String geyserIssue,
            AutoClickerIntegration autoClicker,
            IntegrationAvailability polar,
            String polarIssue
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.viaVersion = Objects.requireNonNull(viaVersion, "viaVersion");
        this.floodgate = Objects.requireNonNull(floodgate, "floodgate");
        this.geyser = Objects.requireNonNull(geyser, "geyser");
        this.geyserIssue = Objects.requireNonNull(geyserIssue, "geyserIssue");
        this.autoClicker = Objects.requireNonNull(autoClicker, "autoClicker");
        this.polar = Objects.requireNonNull(polar, "polar");
        this.polarIssue = Objects.requireNonNull(polarIssue, "polarIssue");
    }

    public static ClientEvidenceCollector discover(JavaPlugin plugin, Clock clock) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(clock, "clock");
        ViaVersionIntegration via = ViaVersionIntegration.discover(
                plugin.getServer().getPluginManager()
        );
        FloodgateIntegration floodgate = FloodgateIntegration.discover(
                plugin.getServer().getPluginManager()
        );
        Plugin geyserPlugin = plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot");
        if (geyserPlugin == null) {
            geyserPlugin = plugin.getServer().getPluginManager().getPlugin("Geyser");
        }
        IntegrationAvailability geyserAvailability = geyserPlugin != null && geyserPlugin.isEnabled()
                ? IntegrationAvailability.AVAILABLE
                : IntegrationAvailability.NOT_INSTALLED;
        String geyserIssue = geyserAvailability == IntegrationAvailability.AVAILABLE
                ? ""
                : "Geyser is not installed or enabled";
        boolean autoClickerEnabled = plugin.getServer().getPluginManager()
                .isPluginEnabled("EnthusiaServerAutoClicker");
        AutoClickerIntegration autoClicker = AutoClickerIntegration.discover(
                plugin.getServer().getServicesManager(),
                autoClickerEnabled
        );
        Plugin polarPlugin = plugin.getServer().getPluginManager().getPlugin("PolarLoader");
        IntegrationAvailability polarAvailability = polarPlugin != null && polarPlugin.isEnabled()
                ? IntegrationAvailability.UNAVAILABLE
                : IntegrationAvailability.NOT_INSTALLED;
        String polarIssue = polarAvailability == IntegrationAvailability.UNAVAILABLE
                ? "PolarLoader exposes no public player metadata API"
                : "PolarLoader is not installed or enabled";
        return new ClientEvidenceCollector(
                clock,
                via,
                floodgate,
                geyserAvailability,
                geyserIssue,
                autoClicker,
                polarAvailability,
                polarIssue
        );
    }

    public ClientEvidenceSnapshot capture(Player player) {
        Objects.requireNonNull(player, "player");
        ViaVersionIntegration.ProtocolObservation via = viaVersion.observe(player.getUniqueId());
        FloodgateIntegration.BedrockObservation bedrock = floodgate.observe(player.getUniqueId());
        AutoClickerIntegration.HandshakeObservation handshake = autoClicker.observe(
                player.getUniqueId()
        );
        Instant capturedAt = clock.instant();
        Optional<Integer> protocol = via.protocolVersion();
        if (protocol.isEmpty() && player.getProtocolVersion() >= 0) {
            protocol = Optional.of(player.getProtocolVersion());
        }
        PlayerPlatform platform = PlayerPlatformDetection.resolve(
                bedrock.availability(),
                bedrock.floodgatePlayer(),
                geyser
        );
        Optional<String> minecraftVersion = bedrock.floodgatePlayer()
                ? bedrock.bedrockVersion().or(() -> via.minecraftVersion())
                : via.minecraftVersion();
        return new ClientEvidenceSnapshot(
                player.getUniqueId(),
                capturedAt,
                platform,
                protocol,
                bounded(minecraftVersion, 64),
                optionalText(player.getClientBrandName(), 255),
                via.availability(),
                bounded(via.pluginVersion(), 64),
                bedrock.availability(),
                bedrock.floodgatePlayer(),
                bounded(bedrock.bedrockVersion(), 64),
                bounded(bedrock.bedrockDevice(), 64),
                geyser,
                handshake.availability(),
                handshake.handshake(),
                polar,
                Optional.empty()
        );
    }

    public Map<String, String> issues() {
        Map<String, String> issues = new LinkedHashMap<>();
        addIssue(issues, "ViaVersion", viaVersion.availability(), viaVersion.issue());
        addIssue(issues, "Floodgate", floodgate.availability(), floodgate.issue());
        addIssue(issues, "Geyser", geyser, geyserIssue);
        addIssue(issues, "Enthusia AutoClicker", autoClicker.availability(), autoClicker.issue());
        addIssue(issues, "Polar", polar, polarIssue);
        return Map.copyOf(issues);
    }

    private static void addIssue(
            Map<String, String> issues,
            String integration,
            IntegrationAvailability availability,
            String issue
    ) {
        if (availability != IntegrationAvailability.AVAILABLE) {
            issues.put(integration, availability + ": " + issue);
        }
    }

    private static Optional<String> optionalText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength));
    }

    private static Optional<String> bounded(Optional<String> value, int maximumLength) {
        return value.flatMap(text -> optionalText(text, maximumLength));
    }
}
