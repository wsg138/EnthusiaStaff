package net.enthusia.staff.paper.visibility;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class VanishManager implements Listener {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final DefaultStaffVisibilityService visibility;
    private final Supplier<VanishStore> store;
    private final Supplier<StaffSessionStore> sessions;
    private final StaffModeManager staffMode;
    private final ExecutorService workers;
    private final Map<UUID, StaffRank> onlineStaffRanks = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenSpectators = ConcurrentHashMap.newKeySet();
    private final SpectatorTabPacketAdapter spectatorTabPackets;

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
        this.spectatorTabPackets = installSpectatorTabPackets();
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
                    plugin.getServer().getOnlinePlayers().forEach(player -> {
                        recordViewerRank(player);
                        applySpectatorPolicy(player, player.getGameMode(), false);
                    });
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
        StaffRank rank = resolveRank(player);
        if (rank == null) {
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

    public void configureSpectatorTab(Player player, boolean appearNormally) {
        StaffRank rank = resolveRank(player);
        if (!SpectatorTabPolicy.offersVisibilityChoice(rank)) {
            player.sendMessage(Component.text("Your staff rank cannot change spectator tab presentation."));
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage(Component.text("Spectator tab presentation is only available while spectating."));
            return;
        }
        if (appearNormally) {
            if (!SpectatorTabPolicy.mayAppearNormally(
                    rank,
                    player.getGameMode(),
                    visibility.isVanished(player.getUniqueId()),
                    spectatorTabPackets.available()
            )) {
                player.sendMessage(Component.text(
                        visibility.isVanished(player.getUniqueId())
                                ? "Disable full vanish before appearing on the tab list."
                                : "ProtocolLib spectator masking is unavailable; you remain hidden from tab."
                ));
                hiddenSpectators.add(player.getUniqueId());
                refreshTarget(player);
                return;
            }
            hiddenSpectators.remove(player.getUniqueId());
            refreshTarget(player);
            player.sendMessage(Component.text("You now appear normally on tab while remaining in spectator. ",
                            NamedTextColor.GREEN)
                    .append(Component.text("[Hide again]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/vanish tab hide"))
                            .hoverEvent(HoverEvent.showText(Component.text("Remove yourself from tab")))));
            return;
        }
        hiddenSpectators.add(player.getUniqueId());
        refreshTarget(player);
        player.sendMessage(Component.text("You are hidden from the tab list while spectating."));
    }

    public void staffModeExited(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !visibility.isVanished(playerId)) {
            return;
        }
        StaffRank rank = resolveRank(player);
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
                    if (vanished) {
                        hiddenSpectators.remove(playerId);
                    } else {
                        applySpectatorPolicy(player, player.getGameMode(), true);
                    }
                    refreshTarget(player);
                    player.sendMessage(Component.text(vanished ? "Vanish enabled." : "Vanish disabled."));
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vanish state change failed", exception);
                message(player, "Vanish change failed; inspect the sanitized server log.");
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        recordViewerRank(player);
        applySpectatorPolicy(player, event.getNewGameMode(), true);
        refreshTarget(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recordViewerRank(player);
        applySpectatorPolicy(player, player.getGameMode(), true);
        if (visibility.isVanished(player.getUniqueId())) {
            event.joinMessage(null);
        }
        refreshViewer(player);
        refreshTarget(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (visibility.isVanished(player.getUniqueId())) {
            event.quitMessage(null);
        }
        UUID playerId = player.getUniqueId();
        visibility.removeViewer(playerId);
        onlineStaffRanks.remove(playerId);
        hiddenSpectators.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin || event.getPlugin().getName().equals("ProtocolLib")) {
            spectatorTabPackets.close();
            if (event.getPlugin() != plugin) {
                packetMaskFailed();
            }
        }
    }

    public void refreshAll() {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            refreshViewer(viewer);
        }
    }

    private void refreshViewer(Player viewer) {
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            refreshPair(viewer, target);
        }
    }

    private void refreshTarget(Player target) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            refreshPair(viewer, target);
        }
    }

    private void refreshPair(Player viewer, Player target) {
        boolean canSee = visibility.canSee(viewer.getUniqueId(), target.getUniqueId());
        if (canSee) {
            viewer.showPlayer(plugin, target);
        } else {
            viewer.hidePlayer(plugin, target);
        }
        applyTabListing(viewer, target, canSee && viewer.canSee(target));
    }

    private void applyTabListing(Player viewer, Player target, boolean canSee) {
        UUID targetId = target.getUniqueId();
        if (!canSee || hiddenSpectators.contains(targetId)) {
            viewer.unlistPlayer(target);
            return;
        }
        StaffRank targetRank = onlineStaffRanks.get(targetId);
        if (targetRank == null) {
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR && !spectatorTabPackets.available()) {
            viewer.unlistPlayer(target);
            return;
        }
        viewer.listPlayer(target);
    }

    private void applySpectatorPolicy(Player player, GameMode gameMode, boolean prompt) {
        UUID playerId = player.getUniqueId();
        StaffRank rank = onlineStaffRanks.get(playerId);
        if (gameMode != GameMode.SPECTATOR || !SpectatorTabPolicy.masksSpectatorEntry(rank)) {
            hiddenSpectators.remove(playerId);
            return;
        }
        if (visibility.isVanished(playerId)) {
            hiddenSpectators.remove(playerId);
            return;
        }
        if (SpectatorTabPolicy.offersVisibilityChoice(rank)) {
            boolean newlyHidden = hiddenSpectators.add(playerId);
            if (prompt && newlyHidden) {
                promptSpectatorChoice(player);
            }
            return;
        }
        if (spectatorTabPackets.available()) {
            hiddenSpectators.remove(playerId);
        } else {
            hiddenSpectators.add(playerId);
        }
    }

    private void promptSpectatorChoice(Player player) {
        Component prompt = Component.text(
                        "You entered spectator and were removed from the tab list. ",
                        NamedTextColor.GRAY
                )
                .append(Component.text("[Vanish]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/vanish"))
                        .hoverEvent(HoverEvent.showText(Component.text("Enter full vanish"))));
        if (spectatorTabPackets.available()) {
            prompt = prompt.append(Component.space())
                    .append(Component.text("[Appear normally]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/vanish tab show"))
                            .hoverEvent(HoverEvent.showText(Component.text(
                                    "Appear on tab as a normal non-spectator entry"
                            ))));
        } else {
            prompt = prompt.append(Component.text(
                    " Normal tab appearance is unavailable because packet masking is not active.",
                    NamedTextColor.RED
            ));
        }
        player.sendMessage(prompt);
    }

    private SpectatorTabPacketAdapter installSpectatorTabPackets() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning(
                    "ProtocolLib is unavailable; spectator staff will be removed from tab instead of exposing spectator state"
            );
            return SpectatorTabPacketAdapter.unavailable();
        }
        try {
            PlayerInfoTabMasker masker = new PlayerInfoTabMasker(
                    visibility::canSee,
                    onlineStaffRanks::get,
                    hiddenSpectators::contains
            );
            return ProtocolLibSpectatorTabPacketAdapter.install(plugin, masker, this::packetMaskFailed);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "ProtocolLib spectator-tab adapter could not start; spectator staff will remain unlisted",
                    failure
            );
            return SpectatorTabPacketAdapter.unavailable();
        }
    }

    private void packetMaskFailed() {
        sync(() -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                StaffRank rank = onlineStaffRanks.get(player.getUniqueId());
                if (player.getGameMode() == GameMode.SPECTATOR
                        && SpectatorTabPolicy.masksSpectatorEntry(rank)
                        && !visibility.isVanished(player.getUniqueId())) {
                    hiddenSpectators.add(player.getUniqueId());
                    refreshTarget(player);
                }
            }
        });
    }

    private StaffRank resolveRank(Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            visibility.removeViewer(player.getUniqueId());
            onlineStaffRanks.remove(player.getUniqueId());
        } else {
            visibility.setViewerRank(player.getUniqueId(), rank);
            onlineStaffRanks.put(player.getUniqueId(), rank);
        }
        return rank;
    }

    private void recordViewerRank(Player player) {
        resolveRank(player);
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
