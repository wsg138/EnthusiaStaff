package net.enthusia.staff.paper.commandbridge;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutputSanitizer;
import net.enthusia.staff.domain.commandbridge.CommandBridgePolicy;
import net.enthusia.staff.domain.commandbridge.CommandBridgeService;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCommandBridgeRuntime implements AutoCloseable {
    private final DiscordCommandBridgeEndpoint endpoint;

    private PaperCommandBridgeRuntime(DiscordCommandBridgeEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public static PaperCommandBridgeRuntime open(
            JavaPlugin plugin,
            String serverId,
            MariaDbRuntime storage,
            PaperCommandBridgeConfiguration configuration
    ) {
        if (plugin == null || storage == null || configuration == null) {
            throw new IllegalArgumentException("command bridge runtime dependencies are required");
        }
        LuckPerms luckPerms = currentLuckPerms(plugin);
        CommandBridgeService service = new CommandBridgeService(
                new CommandBridgePolicy(Set.of(serverId), configuration.rules()),
                new CanonicalCommandBridgeLinkVerifier(storage.discordModerationPersistenceStore()),
                new LuckPermsCommandBridgeAuthorityResolver(luckPerms),
                storage.commandBridgeAuditStore(),
                new BukkitConsoleCommandBridgeExecutor(plugin),
                new CommandBridgeOutputSanitizer(),
                Clock.systemUTC()
        );
        try {
            return new PaperCommandBridgeRuntime(new DiscordCommandBridgeEndpoint(
                    serverId,
                    configuration.bindHost(),
                    configuration.port(),
                    configuration.credential(),
                    service,
                    plugin.getLogger()
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("command bridge endpoint could not bind", exception);
        }
    }

    private static LuckPerms currentLuckPerms(JavaPlugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            throw new IllegalStateException("LuckPerms is required for command bridge authority");
        }
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("LuckPerms command bridge authority is unavailable", exception);
        }
    }

    @Override
    public void close() {
        endpoint.close();
    }
}
