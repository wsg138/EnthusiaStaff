package net.enthusia.staff.paper.visibility;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final long RECONCILIATION_RETRY_SECONDS = 5L;

    private final JavaPlugin plugin;
    private final Clock clock;
    private final DefaultStaffVisibilityService visibility;
    private final Supplier<VanishStore> store;
    private final Supplier<StaffSessionStore> sessions;
    private final StaffModeManager staffMode;
    private final ExecutorService workers;
    private final Map<UUID, StaffRank> onlineStaffRanks = new ConcurrentHashMap<>();
    private final Map<UUID, StaffRank> durableVanishedRanks = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> reconciliationRetryAfter = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenSpectators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRankChecks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> stateWrites = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reconciliationFailureNotified = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean rankReconciliationStarted = new AtomicBoolean();
    private final VanishAudienceCoordinator<Player> audiences;
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
        this.audiences = new VanishAudienceCoordinator<>(this::onEntity, this::refreshPair);
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
                    durableVanishedRanks.put(record.staffId(), record.rank());
                    visibility.setVanished(record.staffId(), record.rank(), true);
                }
                recoverOnlinePlayers();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vanish-state initialization failed", exception);
            }
        });
    }

    public void startRankReconciliation() {
        if (!rankReconciliationStarted.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (UUID playerId : audiences.playerIds()) {
                if (!pendingRankChecks.add(playerId)) {
                    continue;
                }
                audiences.onOwner(
                        playerId,
                        player -> {
                            try {
                                reconcileLiveRank(player);
                            } finally {
                                pendingRankChecks.remove(playerId);
                            }
                        },
                        () -> pendingRankChecks.remove(playerId)
                );
            }
        }, 20L, 20L);
    }

    private void recoverOnlinePlayers() {
        sync(() -> plugin.getServer().getOnlinePlayers().forEach(player ->
                onEntity(player, () -> recoverOnlinePlayer(player))));
    }

    private void recoverOnlinePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        audiences.register(playerId, player, player.getGameMode());
        recordViewerRank(player);
        applySpectatorPolicy(player, player.getGameMode(), false);
        reconcileLiveRank(player);
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
    }

    public boolean isVanished(UUID playerId) {
        return visibility.isVanished(playerId);
    }

    public void toggle(Player player) {
        StaffRank rank = resolveAndPublishRank(player);
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
        StaffRank rank = resolveAndPublishRank(player);
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
                audiences.refreshTarget(player.getUniqueId());
                return;
            }
            hiddenSpectators.remove(player.getUniqueId());
            audiences.refreshTarget(player.getUniqueId());
            player.sendMessage(Component.text("You now appear normally on tab while remaining in spectator. ",
                            NamedTextColor.GREEN)
                    .append(Component.text("[Hide again]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/vanish tab hide"))
                            .hoverEvent(HoverEvent.showText(Component.text("Remove yourself from tab")))));
            return;
        }
        hiddenSpectators.add(player.getUniqueId());
        audiences.refreshTarget(player.getUniqueId());
        player.sendMessage(Component.text("You are hidden from the tab list while spectating."));
    }

    public void staffModeExited(UUID playerId) {
        audiences.onOwner(playerId, player -> disableAfterStaffModeExit(playerId, player));
    }

    private void disableAfterStaffModeExit(UUID playerId, Player player) {
        if (visibility.isVanished(playerId)) {
            StaffRank rank = resolveAndPublishRank(player);
            if (rank != null && requiresStaffMode(rank)) {
                set(player, rank, false);
            }
        }
    }

    private static boolean requiresStaffMode(StaffRank rank) {
        return VanishRankReconciliationPolicy.requiresStaffMode(rank);
    }

    private void set(Player player, StaffRank rank, boolean vanished) {
        UUID playerId = player.getUniqueId();
        if (!stateWrites.add(playerId)) {
            player.sendMessage(Component.text("A vanish state change is already being saved."));
            return;
        }
        if (!submit(() -> {
            try {
                VanishStore loaded = store.get();
                if (loaded == null) {
                    message(playerId, "Vanish storage is not ready; no visibility change was made.");
                    return;
                }
                persistState(loaded, playerId, rank, vanished);
                if (vanished) {
                    durableVanishedRanks.put(playerId, rank);
                } else {
                    durableVanishedRanks.remove(playerId);
                }
                boolean viewerChanged = publishViewerRank(playerId, rank);
                visibility.setVanished(playerId, rank, vanished);
                if (vanished) {
                    hiddenSpectators.remove(playerId);
                }
                reconciliationRetryAfter.remove(playerId);
                reconciliationFailureNotified.remove(playerId);
                audiences.onOwner(playerId, current -> finishSet(current, vanished, viewerChanged));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vanish state change failed", exception);
                message(playerId, "Vanish change failed; inspect the sanitized server log.");
            } finally {
                stateWrites.remove(playerId);
            }
        })) {
            stateWrites.remove(playerId);
            player.sendMessage(Component.text("The bounded work queue is full; vanish was not changed."));
        }
    }

    private void persistState(VanishStore loaded, UUID playerId, StaffRank rank, boolean vanished) {
        Instant now = clock.instant();
        loaded.set(playerId, rank, vanished, playerId, now);
        StaffSessionStore sessionStore = sessions.get();
        if (sessionStore != null && staffMode.active(playerId)) {
            sessionStore.setVanish(playerId, vanished, now);
        }
    }

    private void finishSet(Player player, boolean vanished, boolean viewerChanged) {
        UUID playerId = player.getUniqueId();
        if (!vanished) {
            applySpectatorPolicy(player, player.getGameMode(), true);
        }
        audiences.updateGameMode(playerId, player.getGameMode());
        if (viewerChanged) {
            audiences.refreshViewer(playerId);
        }
        audiences.refreshTarget(playerId);
        player.sendMessage(Component.text(vanished ? "Vanish enabled." : "Vanish disabled."));
    }

    private void reconcileLiveRank(Player player) {
        UUID playerId = player.getUniqueId();
        StaffRank cachedRank = onlineStaffRanks.get(playerId);
        StaffRank liveRank = resolveLiveRank(player);
        VanishRankReconciliationPolicy.ViewerAction viewerAction =
                VanishRankReconciliationPolicy.viewerAction(cachedRank, liveRank);
        boolean viewerChanged = applyViewerAction(playerId, liveRank, viewerAction);
        if (viewerChanged) {
            applySpectatorPolicy(player, player.getGameMode(), false);
            audiences.updateGameMode(playerId, player.getGameMode());
            audiences.refreshViewer(playerId);
            audiences.refreshTarget(playerId);
        }
        if (stateWrites.contains(playerId)) {
            return;
        }
        StaffRank durableRank = durableVanishedRanks.get(playerId);
        boolean vanished = visibility.isVanished(playerId);
        VanishRankReconciliationPolicy.VanishAction vanishAction =
                VanishRankReconciliationPolicy.vanishAction(
                        vanished,
                        durableRank,
                        liveRank,
                        staffMode.active(playerId)
                );
        if (vanishAction == VanishRankReconciliationPolicy.VanishAction.UPDATE_RANK) {
            applyReconciledMemoryState(player, liveRank, true);
            reconcileDurableState(
                    playerId,
                    liveRank,
                    true,
                    "Your vanish visibility was updated for your current staff rank."
            );
        } else if (vanishAction == VanishRankReconciliationPolicy.VanishAction.DISABLE) {
            StaffRank writeRank = firstPlayerRank(durableRank, visibility.vanishedRank(playerId), cachedRank, liveRank);
            applyReconciledMemoryState(player, writeRank, false);
            if (writeRank != null) {
                reconcileDurableState(
                        playerId,
                        writeRank,
                        false,
                        "Vanish was disabled because your current staff rank or staff-mode state no longer permits it."
                );
            }
        }
    }

    private boolean applyViewerAction(
            UUID playerId,
            StaffRank liveRank,
            VanishRankReconciliationPolicy.ViewerAction action
    ) {
        if (action == VanishRankReconciliationPolicy.ViewerAction.UPDATE) {
            onlineStaffRanks.put(playerId, liveRank);
            visibility.setViewerRank(playerId, liveRank);
            return true;
        }
        if (action == VanishRankReconciliationPolicy.ViewerAction.REMOVE) {
            onlineStaffRanks.remove(playerId);
            visibility.removeViewer(playerId);
            return true;
        }
        return false;
    }

    private void applyReconciledMemoryState(Player player, StaffRank rank, boolean vanished) {
        UUID playerId = player.getUniqueId();
        visibility.setVanished(playerId, rank, vanished);
        if (vanished) {
            hiddenSpectators.remove(playerId);
        } else {
            applySpectatorPolicy(player, player.getGameMode(), false);
        }
        audiences.updateGameMode(playerId, player.getGameMode());
        audiences.refreshTarget(playerId);
    }

    private void reconcileDurableState(UUID playerId, StaffRank rank, boolean vanished, String successMessage) {
        Instant retryAfter = reconciliationRetryAfter.get(playerId);
        if (retryAfter != null && clock.instant().isBefore(retryAfter)) {
            return;
        }
        if (!stateWrites.add(playerId)) {
            return;
        }
        if (!submit(() -> {
            try {
                VanishStore loaded = store.get();
                if (loaded == null) {
                    throw new IllegalStateException("vanish storage is not ready");
                }
                persistState(loaded, playerId, rank, vanished);
                if (vanished) {
                    durableVanishedRanks.put(playerId, rank);
                } else {
                    durableVanishedRanks.remove(playerId);
                }
                reconciliationRetryAfter.remove(playerId);
                reconciliationFailureNotified.remove(playerId);
                message(playerId, successMessage);
            } catch (RuntimeException exception) {
                reconciliationFailed(playerId, exception);
            } finally {
                stateWrites.remove(playerId);
            }
        })) {
            stateWrites.remove(playerId);
            reconciliationFailed(
                    playerId,
                    new RejectedExecutionException("bounded vanish reconciliation queue is full")
            );
        }
    }

    private void reconciliationFailed(UUID playerId, RuntimeException exception) {
        reconciliationRetryAfter.put(playerId, clock.instant().plusSeconds(RECONCILIATION_RETRY_SECONDS));
        plugin.getLogger().log(Level.SEVERE, "Vanish rank reconciliation failed; retry remains pending", exception);
        if (reconciliationFailureNotified.add(playerId)) {
            message(
                    playerId,
                    "Vanish rank reconciliation could not be saved; visibility is fail-safe and will retry."
            );
        }
    }

    private static StaffRank firstPlayerRank(StaffRank... candidates) {
        for (StaffRank candidate : candidates) {
            if (VanishRankReconciliationPolicy.isPlayerRank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        boolean viewerChanged = recordViewerRank(player);
        applySpectatorPolicy(player, event.getNewGameMode(), true);
        audiences.updateGameMode(player.getUniqueId(), event.getNewGameMode());
        if (viewerChanged) {
            audiences.refreshViewer(player.getUniqueId());
        }
        audiences.refreshTarget(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        audiences.register(playerId, player, player.getGameMode());
        recordViewerRank(player);
        applySpectatorPolicy(player, player.getGameMode(), true);
        reconcileLiveRank(player);
        if (visibility.isVanished(playerId)) {
            event.joinMessage(null);
        }
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        reconcileLiveRank(player);
        if (visibility.isVanished(player.getUniqueId())) {
            event.quitMessage(null);
        }
        UUID playerId = player.getUniqueId();
        audiences.remove(playerId);
        visibility.removeViewer(playerId);
        onlineStaffRanks.remove(playerId);
        hiddenSpectators.remove(playerId);
        pendingRankChecks.remove(playerId);
        reconciliationRetryAfter.remove(playerId);
        reconciliationFailureNotified.remove(playerId);
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
        audiences.refreshAll();
    }

    private void refreshPair(
            VanishAudienceCoordinator.OnlineEntity<Player> viewerEntry,
            VanishAudienceCoordinator.OnlineEntity<Player> targetEntry
    ) {
        Player viewer = viewerEntry.owner();
        Player target = targetEntry.owner();
        boolean canSee = visibility.canSee(viewerEntry.playerId(), targetEntry.playerId());
        try {
            if (canSee) {
                viewer.showPlayer(plugin, target);
            } else {
                viewer.hidePlayer(plugin, target);
            }
            applyTabListing(viewer, target, targetEntry, canSee && viewer.canSee(target));
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Player visibility refresh raced with disconnect", exception);
        }
    }

    private void applyTabListing(
            Player viewer,
            Player target,
            VanishAudienceCoordinator.OnlineEntity<Player> targetEntry,
            boolean canSee
    ) {
        if (!shouldList(targetEntry, canSee)) {
            unlistSafely(viewer, target);
            return;
        }
        try {
            viewer.listPlayer(target);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Player tab listing raced with visibility removal", exception);
            unlistSafely(viewer, target);
        }
    }

    private boolean shouldList(VanishAudienceCoordinator.OnlineEntity<Player> target, boolean canSee) {
        UUID targetId = target.playerId();
        return SpectatorTabPolicy.shouldList(
                onlineStaffRanks.get(targetId),
                target.gameMode(),
                canSee,
                hiddenSpectators.contains(targetId),
                spectatorTabPackets.available()
        );
    }

    private void unlistSafely(Player viewer, Player target) {
        try {
            viewer.unlistPlayer(target);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Player tab removal raced with disconnect", exception);
        }
    }

    private void applySpectatorPolicy(Player player, GameMode gameMode, boolean prompt) {
        UUID playerId = player.getUniqueId();
        StaffRank rank = onlineStaffRanks.get(playerId);
        if (!requiresSpectatorMask(playerId, rank, gameMode)) {
            hiddenSpectators.remove(playerId);
            return;
        }
        if (SpectatorTabPolicy.offersVisibilityChoice(rank)) {
            applySpectatorChoice(player, playerId, prompt);
            return;
        }
        if (spectatorTabPackets.available()) {
            hiddenSpectators.remove(playerId);
        } else {
            hiddenSpectators.add(playerId);
        }
    }

    private boolean requiresSpectatorMask(UUID playerId, StaffRank rank, GameMode gameMode) {
        return gameMode == GameMode.SPECTATOR
                && SpectatorTabPolicy.masksSpectatorEntry(rank)
                && !visibility.isVanished(playerId);
    }

    private void applySpectatorChoice(Player player, UUID playerId, boolean prompt) {
        boolean newlyHidden = hiddenSpectators.add(playerId);
        if (prompt && newlyHidden) {
            promptSpectatorChoice(player);
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
        audiences.forEachOwner(this::applyPacketMaskFailure);
    }

    private void applyPacketMaskFailure(Player player) {
        UUID playerId = player.getUniqueId();
        GameMode gameMode = player.getGameMode();
        boolean viewerChanged = recordViewerRank(player);
        applySpectatorPolicy(player, gameMode, false);
        audiences.updateGameMode(playerId, gameMode);
        if (viewerChanged) {
            audiences.refreshViewer(playerId);
        }
        audiences.refreshTarget(playerId);
    }

    private StaffRank resolveAndPublishRank(Player player) {
        StaffRank rank = resolveLiveRank(player);
        if (publishViewerRank(player.getUniqueId(), rank)) {
            audiences.refreshViewer(player.getUniqueId());
        }
        return rank;
    }

    private StaffRank resolveLiveRank(Player player) {
        return PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
    }

    private boolean recordViewerRank(Player player) {
        return publishViewerRank(player.getUniqueId(), resolveLiveRank(player));
    }

    private boolean publishViewerRank(UUID playerId, StaffRank rank) {
        StaffRank previous = onlineStaffRanks.get(playerId);
        if (rank == null) {
            visibility.removeViewer(playerId);
            onlineStaffRanks.remove(playerId);
        } else {
            visibility.setViewerRank(playerId, rank);
            onlineStaffRanks.put(playerId, rank);
        }
        return previous != rank;
    }

    private boolean submit(Runnable operation) {
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Vanish operation skipped because the bounded worker queue is full");
            return false;
        }
    }

    private void sync(Runnable operation) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, operation);
    }

    private void message(UUID playerId, String message) {
        audiences.onOwner(playerId, player -> player.sendMessage(Component.text(message)));
    }

    private boolean onEntity(Player player, Runnable operation) {
        return player.getScheduler().execute(plugin, operation, null, 1L);
    }
}
