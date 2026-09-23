package net.enthusia.staff.paper.visibility;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.Iterator;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
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
        int reportedPlayers = event.getNumPlayers();
        int removed = removeVanished(event.iterator(), vanish::isVanished);
        if (!event.shouldHidePlayers() && removed > 0 && event.getNumPlayers() == reportedPlayers) {
            event.setNumPlayers(visibleCount(reportedPlayers, removed));
        }
    }

    static int removeVanished(Iterator<Player> players, Predicate<UUID> vanished) {
        int removed = 0;
        while (players.hasNext()) {
            Player player = players.next();
            if (vanished.test(player.getUniqueId())) {
                players.remove();
                removed++;
            }
        }
        return removed;
    }

    static int visibleCount(int reportedPlayers, int vanishedPlayers) {
        return Math.max(0, reportedPlayers - Math.max(0, vanishedPlayers));
    }
}
