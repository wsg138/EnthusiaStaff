package net.enthusia.staff.paper.account;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.application.AccountLinkingService.MinecraftOnlineVerifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thread-safe online-player view for account-link workers.
 * Bukkit access is confined to registration/event callbacks; async persistence workers only read the UUID set.
 */
public final class PaperOnlinePlayerVerifier implements MinecraftOnlineVerifier, Listener {
    private final Set<UUID> onlinePlayerIds = ConcurrentHashMap.newKeySet();

    PaperOnlinePlayerVerifier(Collection<UUID> initialOnlinePlayers) {
        if (initialOnlinePlayers == null) {
            throw new IllegalArgumentException("initialOnlinePlayers must be present");
        }
        onlinePlayerIds.addAll(initialOnlinePlayers);
    }

    public static PaperOnlinePlayerVerifier register(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must be present");
        }
        Set<UUID> initial = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        PaperOnlinePlayerVerifier verifier = new PaperOnlinePlayerVerifier(initial);
        plugin.getServer().getPluginManager().registerEvents(verifier, plugin);
        return verifier;
    }

    @Override
    public boolean isOnline(UUID minecraftPlayerId) {
        return minecraftPlayerId != null && onlinePlayerIds.contains(minecraftPlayerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        markOnline(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        markOffline(event.getPlayer().getUniqueId());
    }

    void markOnline(UUID playerId) {
        if (playerId != null) {
            onlinePlayerIds.add(playerId);
        }
    }

    void markOffline(UUID playerId) {
        if (playerId != null) {
            onlinePlayerIds.remove(playerId);
        }
    }
}
