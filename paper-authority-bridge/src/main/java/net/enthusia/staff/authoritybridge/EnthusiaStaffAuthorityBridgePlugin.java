package net.enthusia.staff.authoritybridge;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Owner-authorized D16 bridge: rank authority plus an optional transition-only data collector. */
public final class EnthusiaStaffAuthorityBridgePlugin extends JavaPlugin {
    private AuthorityEndpoint endpoint;
    private TransitionDataCollector collector;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            startAuthority();
        } catch (IOException | RuntimeException exception) {
            getLogger().log(Level.SEVERE,
                    "enthusiastaff_authority_bridge_start_failed type={0}",
                    exception.getClass().getSimpleName());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        startCollectorIfConfigured();
    }

    private void startAuthority() throws IOException {
        AuthorityBridgeConfiguration.Value configuration =
                AuthorityBridgeConfiguration.load(getDataFolder().toPath());
        LuckPerms luckPerms = LuckPermsProvider.get();
        endpoint = AuthorityEndpoint.start(configuration, luckPerms, getLogger());
        getLogger().info(
                "enthusiastaff_authority_bridge_started private_only=true commands=0 moderation_mutations=0");
    }

    private void startCollectorIfConfigured() {
        try {
            Optional<TransitionCollectorConfiguration.Value> configuration =
                    TransitionCollectorConfiguration.loadIfPresent(getDataFolder().toPath());
            if (configuration.isEmpty()) {
                getLogger().info("enthusiastaff_transition_collector_disabled reason=no_config");
                return;
            }
            collector = TransitionDataCollector.open(this, configuration.orElseThrow());
            collector.start();
            getLogger().info(
                    "enthusiastaff_transition_collector_started migrations=true discordsrv=read_only litebans=untouched");
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING,
                    "enthusiastaff_transition_collector_start_failed authority_remains=true type={0}",
                    exception.getClass().getSimpleName());
        }
    }

    @Override
    public void onDisable() {
        if (collector != null) {
            collector.close();
        }
        if (endpoint != null) {
            endpoint.close();
        }
    }
}
