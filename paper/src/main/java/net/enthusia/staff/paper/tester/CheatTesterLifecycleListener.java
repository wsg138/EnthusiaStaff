package net.enthusia.staff.paper.tester;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.tester.CheatTesterSessionState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class CheatTesterLifecycleListener implements Listener {
    private final JavaPlugin plugin;
    private final CheatTesterManager manager;

    CheatTesterLifecycleListener(JavaPlugin plugin, CheatTesterManager manager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        CheatTesterSession session = manager.activeSession(event.getPlayer().getUniqueId());
        if (session == null || !session.type.mutatesTargetState()) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        manager.retireForRecovery(session, "Target died during tester; exact restore deferred to respawn");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().execute(plugin, () -> manager.recover(player), null, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        manager.recover(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        manager.clearSelection(playerId);
        finishTargetDisconnect(playerId);
        finishStaffDisconnect(playerId);
    }

    private void finishTargetDisconnect(UUID playerId) {
        CheatTesterSession targetSession = manager.activeSession(playerId);
        if (targetSession == null) {
            return;
        }
        if (targetSession.type == net.enthusia.staff.domain.tester.CheatTesterType.FAKE_ENTITY) {
            manager.finish(targetSession, CheatTesterSessionState.CANCELLED, "target disconnected");
        } else {
            manager.retireForRecovery(targetSession, "Target disconnected during tester");
        }
    }

    private void finishStaffDisconnect(UUID playerId) {
        for (CheatTesterSession session : manager.activeSessions()) {
            if (session.staffId.equals(playerId)) {
                manager.finish(session, CheatTesterSessionState.CANCELLED, "controlling staff disconnected");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            manager.close();
        } else if (event.getPlugin().getName().equals("ProtocolLib")) {
            manager.protocolLibDisabled();
        }
    }
}
