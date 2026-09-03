package net.enthusia.staff.authoritybridge;

import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Owner-authorized, acceptance-only Paper bridge with no player-facing functionality. */
public final class EnthusiaStaffAuthorityBridgePlugin extends JavaPlugin {
    private AuthorityEndpoint endpoint;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            AuthorityBridgeConfiguration.Value configuration =
                    AuthorityBridgeConfiguration.load(getDataFolder().toPath());
            LuckPerms luckPerms = LuckPermsProvider.get();
            endpoint = AuthorityEndpoint.start(configuration, luckPerms, getLogger());
            getLogger().info(
                    "enthusiastaff_authority_bridge_started private_only=true commands=0 listeners=0 database=none"
            );
        } catch (IOException | RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "enthusiastaff_authority_bridge_start_failed type={0}",
                    exception.getClass().getSimpleName()
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (endpoint != null) {
            endpoint.close();
        }
    }
}
