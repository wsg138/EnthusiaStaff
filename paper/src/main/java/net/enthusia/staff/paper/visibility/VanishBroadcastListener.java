package net.enthusia.staff.paper.visibility;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Removes vanished staff from public broadcast and server-list surfaces.
 */
public final class VanishBroadcastListener implements Listener {
    private final VanishManager vanish;

    public VanishBroadcastListener(VanishManager vanish) {
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId())) {
            event.deathMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId())) {
            event.message(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(PaperServerListPingEvent event) {
        event.getListedPlayers().removeIf(entry -> vanish.isVanished(entry.id()));
        if (!event.shouldHidePlayers()) {
            event.setNumPlayers(visibleCount(event.getNumPlayers(), vanish.vanishedOnlineCount()));
        }
    }

    static int visibleCount(int reportedPlayers, int vanishedPlayers) {
        return Math.max(0, reportedPlayers - Math.max(0, vanishedPlayers));
    }
}
