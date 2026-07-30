package net.enthusia.staff.paper.visibility;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.ports.VanishStore;
import net.enthusia.staff.domain.staff.VanishRecord;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class VanishManager implements Listener {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final DefaultStaffVisibilityService visibility;
    private final Supplier<VanishStore> store;
    private final Supplier<StaffSessionStore> sessions;
    private final StaffModeManager staffMode;
    private final ExecutorService workers;

    public VanishManager(
            JavaPlugin plugin,
            Clock clock,
            DefaultStaffVisibilityService visibility,
            Supplier<VanishStore> store,
            Supplier<StaffSessionStore> sessions,
            StaffModeManager staffMode,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.visibility = visibility;
        this.store = store;
        this.sessions = sessions;
        this.staffMode = staffMode;
        this.workers = workers;
    }

    public void initialize() {
        submit(() -> {
            VanishStore loaded = store.get();
            if (loaded == null) {
                return;
            }
            try {
                for (VanishRecord record : loaded.active(10_000)) {
                    visibility.setVanished(record.staffId(), record.rank(), true);
                }
                sync(() -> {
                    plugin.getServer().getOnlinePlayers().forEach(this::recordViewerRank);
                    refreshAll();
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vanish-state initialization failed", exception);
            }
        });
    }

    public boolean isVanished(UUID playerId) {
        return visibility.isVanished(playerId);
    }

    public void toggle(Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            visibility.removeViewer(player.getUniqueId());
            player.sendMessage(Component.text("An explicit EnthusiaStaff rank is required before using vanish."));
            return;
        }
        if (requiresStaffMode(rank) && !staffMode.active(player.getUniqueId())) {
            player.sendMessage(Component.text("Your rank requires active staff mode before vanishing."));
            return;
        }
        boolean next = !visibility.isVanished(player.getUniqueId());
        set(player, rank, next);
    }

    public void staffModeExited(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !visibility.isVanished(playerId)) {
            return;
        }
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank != null && requiresStaffMode(rank)) {
            set(player, rank, false);
        }
    }

    private static boolean requiresStaffMode(StaffRank rank) {
        return rank == StaffRank.HELPER || rank == StaffRank.MOD || rank == StaffRank.DEVELOPER;
    }

    private void set(Player player, StaffRank rank, boolean vanished) {
        UUID playerId = player.getUniqueId();
        submit(() -> {
            VanishStore loaded = store.get();
            if (loaded == null) {
                message(player, "Vanish storage is not ready; no visibility change was made.");
                return;
            }
            try {
                loaded.set(playerId, rank, vanished, playerId, clock.instant());
                StaffSessionStore sessionStore = sessions.get();
                if (sessionStore != null && staffMode.active(playerId)) {
                    sessionStore.setVanish(playerId, vanished, clock.instant());
                }
                sync(() -> {
                    visibility.setViewerRank(playerId, rank);
                    visibility.setVanished(playerId, rank, vanished);
                    refreshAll();
                    player.sendMessage(Component.text(vanished ? "Vanish enabled." : "Vanish disabled."));
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vanish state change failed", exception);
                message(player, "Vanish change failed; inspect the sanitized server log.");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recordViewerRank(player);
        if (visibility.isVanished(player.getUniqueId())) {
            event.joinMessage(null);
        }
        refreshAll();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (visibility.isVanished(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
        visibility.removeViewer(event.getPlayer().getUniqueId());
    }

    public void refreshAll() {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (visibility.canSee(viewer.getUniqueId(), target.getUniqueId())) {
                    viewer.showPlayer(plugin, target);
                } else {
                    viewer.hidePlayer(plugin, target);
                }
            }
        }
    }

    private void recordViewerRank(Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            visibility.removeViewer(player.getUniqueId());
        } else {
            visibility.setViewerRank(player.getUniqueId(), rank);
        }
    }

    private void submit(Runnable operation) {
        try {
            workers.execute(operation);
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Vanish operation skipped because the bounded worker queue is full");
        }
    }

    private void sync(Runnable operation) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, operation);
    }

    private void message(Player player, String message) {
        player.getScheduler().execute(plugin, () -> player.sendMessage(Component.text(message)), null, 1L);
    }
}
