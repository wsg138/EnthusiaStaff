package net.enthusia.staff.paper.visibility;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final int FULL_RANK_SCAN_INTERVAL_PASSES = 5;
    private final JavaPlugin plugin;
    private final Clock clock;
    private final DefaultStaffVisibilityService visibility;
    private final Supplier<VanishStore> store;
    private final Supplier<StaffSessionStore> sessions;
    private final StaffModeManager staffMode;
    private final ExecutorService workers;
    private final Map<UUID, StaffRank> onlineStaffRanks = new ConcurrentHashMap<>();
    private final Map<UUID, StaffRank> durableVanishedRanks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> durableStaffSessionPresence = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> reconciliationRetryAfter = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> staffSessionCheckRetryAfter = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingStaffSessionChecks = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> selectedGameModes = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenSpectators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRankChecks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingDurableLoads = ConcurrentHashMap.newKeySet();
    private final Set<UUID> stateWrites = ConcurrentHashMap.newKeySet();
    private final Set<UUID> selectedModeWrites = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishGameModeApplications = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reconciliationFailureNotified = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffSessionCheckFailureNotified = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingStaffModeExitDisables = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean rankReconciliationStarted = new AtomicBoolean();
    private final AtomicBoolean durableVanishLoaded = new AtomicBoolean();
    private final AtomicInteger rankReconciliationPass = new AtomicInteger();
    private final VanishAudienceCoordinator<Player> audiences;
    private final SpectatorTabPacketAdapter spectatorTabPackets;
    private final SilentContainerTracker silentContainers;
    private final SilentContainerPacketAdapter silentContainerPackets;
    private volatile PresenceTransitionSink presenceTransitionSink = (subjectId, viewerId, vanished) -> {
    };

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
        this.silentContainers = new SilentContainerTracker(visibility::isVanished, clock);
        this.silentContainerPackets = installSilentContainerPackets();
    }

    /**
     * Tracker for silent container opens by vanished staff. Register as a
     * Bukkit listener so container open/close events are observed.
     */
    public SilentContainerTracker silentContainerTracker() {
        return silentContainers;
    }

    public void initialize() {
        durableVanishLoaded.set(false);
        submit(() -> {
            VanishStore loaded = store.get();
            if (loaded == null) {
                return;
            }
            try {
                for (VanishRecord record : loaded.active(10_000)) {
                    durableVanishedRanks.put(record.staffId(), record.rank());
                    rememberPersistedGameMode(record);
                    visibility.setVanished(record.staffId(), record.rank(), true);
                }
                durableVanishLoaded.set(true);
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
            boolean fullScan = nextRankReconciliationPass() == 0;
            for (UUID playerId : audiences.playerIds()) {
                if (!shouldCheckRank(playerId, fullScan) || !pendingRankChecks.add(playerId)) {
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

    private int nextRankReconciliationPass() {
        return rankReconciliationPass.getAndUpdate(
                current -> (current + 1) % FULL_RANK_SCAN_INTERVAL_PASSES
        );
    }

    private boolean shouldCheckRank(UUID playerId, boolean fullScan) {
        return fullScan
                || staffMode.active(playerId)
                || onlineStaffRanks.containsKey(playerId)
                || durableVanishedRanks.containsKey(playerId)
                || pendingStaffModeExitDisables.contains(playerId);
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
        reconcileVanishGameMode(player);
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
    }

    public boolean isVanished(UUID playerId) {
        return visibility.isVanished(playerId);
    }

    /**
     * Returns whether RoseChat may safely decide a real login/logout presence message for this player.
     * A currently vanished player is always known immediately; otherwise the initial durable vanish
     * scan must have completed so a cold-start login cannot leak a persisted vanish state.
     */
    public boolean presenceStateReady(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return visibility.isVanished(playerId) || durableVanishLoaded.get();
    }

    public void setPresenceTransitionSink(PresenceTransitionSink sink) {
        presenceTransitionSink = Objects.requireNonNull(sink, "sink");
    }

    public void clearPresenceTransitionSink() {
        presenceTransitionSink = (subjectId, viewerId, vanished) -> {
        };
    }

    /**
     * Cross-server transfer hook (overnight/cross-server). Read-only: returns the staff
     * member's selected vanish game mode, if known, for inclusion in a transfer snapshot.
     * Does not change any vanish state.
     */
    public java.util.Optional<GameMode> transferSelectedGameMode(UUID playerId) {
        if (playerId == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(selectedGameModes.get(playerId));
    }

    /**
     * Updates the staff member's real selected gameplay mode independently from vanish.
     *
     * <p>Vanish is only a visibility/privacy state. Developer/Admin/Founder may use any real
     * vanilla game mode; Helper/Mod may use protected Survival or real Spectator.</p>
     */
    public boolean selectGameplayMode(Player player, GameMode selected) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(selected, "selected");
        UUID playerId = player.getUniqueId();
        StaffRank rank = resolveLiveRank(player);
        if (!staffMode.active(playerId) || !isSelectableGameMode(rank, selected)) {
            return false;
        }
        selectedGameModes.put(playerId, selected);
        if (visibility.isVanished(playerId)) {
            persistSelectedGameMode(playerId, rank, selected);
        }
        if (player.getGameMode() != selected) {
            player.setGameMode(selected);
        }
        return player.getGameMode() == selected;
    }

    /**
     * Cross-server transfer hook (overnight/cross-server). Applies a vanish state snapshot
     * carried from the source backend, before the join-message logic runs on arrival.
     *
     * <p>The transferred snapshot is authoritative for this session: it is applied
     * synchronously from in-memory state with no database write. The existing asynchronous
     * durable-vanish load remains the fallback and reconciles with the database afterwards.
     * When the snapshot says the player was not vanished this is a no-op.</p>
     */
    public void applyTransferSnapshot(
            Player player,
            net.enthusia.staff.domain.staff.StaffTransferSnapshot snapshot
    ) {
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.vanished()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!snapshot.playerId().equals(playerId)) {
            throw new IllegalArgumentException("transfer snapshot belongs to a different player");
        }
        StaffRank rank = snapshot.rank() != null
                ? snapshot.rank()
                : PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            plugin.getLogger().warning(
                    "Ignoring cross-server vanish snapshot for " + playerId
                            + ": no staff rank available on this backend");
            return;
        }
        durableVanishedRanks.put(playerId, rank);
        if (snapshot.selectedGameMode() != null) {
            try {
                selectedGameModes.put(playerId, GameMode.valueOf(snapshot.selectedGameMode()));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(
                        "Ignoring invalid transferred vanish selected game mode for " + playerId);
            }
        }
        visibility.setVanished(playerId, rank, true);
        if (!staffMode.transitioning(playerId)) {
            reconcileVanishedGameMode(player);
        }
        audiences.updateGameMode(playerId, player.getGameMode());
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
        plugin.getLogger().info(
                "Applied cross-server vanish snapshot for " + player.getName()
                        + " from backend " + snapshot.sourceServer());
    }

    public boolean canSee(UUID viewerId, UUID targetId) {
        return visibility.canSee(viewerId, targetId);
    }

    int vanishedOnlineCount() {
        return (int) audiences.playerIds().stream()
                .filter(this::isVanished)
                .count();
    }

    public void toggle(Player player) {
        StaffRank rank = resolveAndPublishRank(player);
        if (rank == null) {
            player.sendMessage(StaffMessageStyle.style(Component.text("An explicit EnthusiaStaff rank is required before using vanish.")));
            return;
        }
        if (requiresStaffMode(rank) && !staffMode.active(player.getUniqueId())) {
            player.sendMessage(StaffMessageStyle.style(Component.text("Your rank requires active staff mode before vanishing.")));
            return;
        }
        boolean next = !visibility.isVanished(player.getUniqueId());
        set(player, rank, next, true);
    }

    /** Enables rather than toggles: entering while already vanished must never reveal staff. */
    public void staffModeEntered(Player player) {
        UUID playerId = player.getUniqueId();
        UUID sessionId = staffMode.activeSessionId(playerId);
        StaffRank rank = resolveAndPublishRank(player);
        if (rank == null && sessionId != null) {
            player.sendMessage(StaffMessageStyle.error("Your staff rank is unavailable; leaving Staff Mode."));
            staffMode.exit(player);
            return;
        }
        if (rank != null && staffMode.active(player.getUniqueId()) && !isVanished(player.getUniqueId())) {
            set(player, rank, true, true).thenAccept(enabled -> {
                if (!enabled) {
                    exitAfterEntryVanishFailure(playerId, sessionId);
                }
            });
        }
    }

    private void exitAfterEntryVanishFailure(UUID playerId, UUID sessionId) {
                    audiences.onOwner(playerId, current -> {
                        if (sessionId != null && sessionId.equals(staffMode.activeSessionId(playerId))) {
                            current.sendMessage(StaffMessageStyle.error(
                                    "Automatic vanish could not be saved; leaving Staff Mode and restoring your snapshot."));
                            staffMode.exit(current);
                        }
                    });
    }

    public void configureSpectatorTab(Player player, boolean appearNormally) {
        StaffRank rank = resolveAndPublishRank(player);
        if (!SpectatorTabPolicy.offersVisibilityChoice(rank)) {
            player.sendMessage(StaffMessageStyle.style(Component.text("Your staff rank cannot change spectator tab presentation.")));
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage(StaffMessageStyle.style(Component.text("Spectator tab presentation is only available while spectating.")));
            return;
        }
        if (appearNormally) {
            if (!SpectatorTabPolicy.mayAppearNormally(
                    rank,
                    player.getGameMode(),
                    visibility.isVanished(player.getUniqueId()),
                    spectatorTabPackets.available()
            )) {
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        visibility.isVanished(player.getUniqueId())
                                ? "Disable full vanish before appearing on the tab list."
                                : "ProtocolLib spectator masking is unavailable; you remain hidden from tab."
                )));
                hiddenSpectators.add(player.getUniqueId());
                audiences.refreshTarget(player.getUniqueId());
                return;
            }
            hiddenSpectators.remove(player.getUniqueId());
            audiences.refreshTarget(player.getUniqueId());
            player.sendMessage(StaffMessageStyle.style(Component.text("You now appear normally on tab while remaining in spectator. ",
                            NamedTextColor.GREEN)
                    .append(Component.text("[Hide again]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/vanish tab hide"))
                            .hoverEvent(HoverEvent.showText(Component.text("Remove yourself from tab"))))));
            return;
        }
        hiddenSpectators.add(player.getUniqueId());
        audiences.refreshTarget(player.getUniqueId());
        player.sendMessage(StaffMessageStyle.style(Component.text("You are hidden from the tab list while spectating.")));
    }

    public void staffModeExited(UUID playerId) {
        audiences.onOwner(playerId, player -> disableAfterStaffModeExit(playerId, player));
    }

    /**
     * Marks a game-mode change as plugin-initiated so {@link #onGameModeChange} can distinguish
     * Staff Mode profile transitions from player-selected changes while vanish is active.
     */
    public void beginPluginGameModeApplication(UUID playerId) {
        vanishGameModeApplications.add(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Clears the plugin-initiated game-mode marker installed by {@link #beginPluginGameModeApplication}. */
    public void endPluginGameModeApplication(UUID playerId) {
        vanishGameModeApplications.remove(playerId);
    }

    private void disableAfterStaffModeExit(UUID playerId, Player player) {
        durableStaffSessionPresence.put(playerId, false);
        StaffRank rank = resolveAndPublishRank(player);
        if (rank == null) {
            pendingStaffModeExitDisables.remove(playerId);
            return;
        }
        if (!requiresStaffMode(rank)) {
            pendingStaffModeExitDisables.remove(playerId);
            // Admin/Founder vanish and real game mode are independent. Saved-state restoration
            // may restore the exact pre-staff mode while visibility remains vanished; reconciliation
            // must preserve that real mode rather than forcing Spectator.
            reconcileVanishGameMode(player);
            return;
        }
        pendingStaffModeExitDisables.add(playerId);
        if (visibility.isVanished(playerId) || durableVanishedRanks.containsKey(playerId)) {
            set(player, rank, false, false);
        } else {
            pendingStaffModeExitDisables.remove(playerId);
        }
    }

    private static boolean requiresStaffMode(StaffRank rank) {
        return VanishRankReconciliationPolicy.requiresStaffMode(rank);
    }

    private java.util.concurrent.CompletableFuture<Boolean> set(
            Player player, StaffRank rank, boolean vanished, boolean restoreSelectedMode) {
        UUID playerId = player.getUniqueId();
        GameMode selectedGameMode = vanished
                ? selectedGameModeForEnable(player, rank)
                : selectedGameModes.get(playerId);
        if (!stateWrites.add(playerId)) {
            player.sendMessage(StaffMessageStyle.style(Component.text("A vanish state change is already being saved.")));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        java.util.concurrent.CompletableFuture<Boolean> result = new java.util.concurrent.CompletableFuture<>();
        UUID expectedSession = staffMode.activeSessionId(playerId);
        if (!submit(() -> {
            if (expectedSession != null && !expectedSession.equals(staffMode.activeSessionId(playerId))) {
                stateWrites.remove(playerId);
                result.complete(false);
                return;
            }
            result.complete(persistSet(playerId, rank, vanished, restoreSelectedMode, selectedGameMode));
        })) {
            stateWrites.remove(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text("The bounded work queue is full; vanish was not changed.")));
            result.complete(false);
        }
        return result;
    }

    private boolean persistSet(
            UUID playerId,
            StaffRank rank,
            boolean vanished,
            boolean restoreSelectedMode,
            GameMode selectedGameMode
    ) {
        try {
            VanishStore loaded = store.get();
            if (loaded == null) {
                message(playerId, "Vanish storage is not ready; no visibility change was made.");
                return false;
            }
            persistState(loaded, playerId, rank, vanished, selectedGameMode);
            rememberCommittedState(playerId, rank, vanished, restoreSelectedMode, selectedGameMode);
            boolean viewerChanged = publishViewerRank(playerId, rank);
            Set<UUID> hiddenBefore = vanished ? Set.of() : hiddenPresenceViewers(playerId);
            visibility.setVanished(playerId, rank, vanished);
            Set<UUID> presenceViewers = vanished ? hiddenPresenceViewers(playerId) : hiddenBefore;
            if (vanished) {
                hiddenSpectators.remove(playerId);
            }
            reconciliationRetryAfter.remove(playerId);
            reconciliationFailureNotified.remove(playerId);
            audiences.onOwner(
                    playerId,
                    current -> finishSet(
                            current,
                            vanished,
                            viewerChanged,
                            restoreSelectedMode,
                            presenceViewers
                    )
            );
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Vanish state change failed", exception);
            message(playerId, "Vanish change failed; inspect the sanitized server log.");
            return false;
        } finally {
            stateWrites.remove(playerId);
        }
    }

    private void rememberCommittedState(
            UUID playerId,
            StaffRank rank,
            boolean vanished,
            boolean restoreSelectedMode,
            GameMode selectedGameMode
    ) {
        if (vanished) {
            durableVanishedRanks.put(playerId, rank);
            selectedGameModes.put(playerId, selectedGameMode);
            return;
        }
        durableVanishedRanks.remove(playerId);
        if (!restoreSelectedMode) {
            selectedGameModes.remove(playerId);
        }
        pendingStaffModeExitDisables.remove(playerId);
    }

    private void persistState(
            VanishStore loaded,
            UUID playerId,
            StaffRank rank,
            boolean vanished,
            GameMode selectedGameMode
    ) {
        Instant now = clock.instant();
        VanishStore.WriteResult result = loaded.set(
                playerId,
                rank,
                vanished,
                playerId,
                now,
                staffMode.active(playerId),
                selectedGameMode == null ? null : selectedGameMode.name()
        );
        if (result == VanishStore.WriteResult.STAFF_SESSION_NOT_ACTIVE) {
            throw new IllegalStateException("active staff session ended before vanish state commit");
        }
    }

    private void finishSet(
            Player player,
            boolean vanished,
            boolean viewerChanged,
            boolean restoreSelectedMode,
            Set<UUID> presenceViewers
    ) {
        UUID playerId = player.getUniqueId();
        if (vanished) {
            if (!staffMode.transitioning(playerId)) {
                reconcileVanishedGameMode(player);
            }
        } else if (restoreSelectedMode) {
            restoreSelectedGameMode(player);
        } else {
            selectedGameModes.remove(playerId);
        }
        applySpectatorPolicy(player, player.getGameMode(), true);
        audiences.updateGameMode(playerId, player.getGameMode());
        if (viewerChanged) {
            audiences.refreshViewer(playerId);
        }
        audiences.refreshTarget(playerId);
        publishPresenceTransition(playerId, vanished, presenceViewers);
        player.sendMessage(StaffMessageStyle.style(Component.text(vanished ? "Vanish enabled." : "Vanish disabled.")));
    }

    private Set<UUID> hiddenPresenceViewers(UUID subjectId) {
        return Set.copyOf(audiences.playerIds().stream()
                .filter(viewerId -> !viewerId.equals(subjectId))
                .filter(viewerId -> !visibility.canSee(viewerId, subjectId))
                .toList());
    }

    private void publishPresenceTransition(UUID subjectId, boolean vanished, Set<UUID> viewerIds) {
        PresenceTransitionSink sink = presenceTransitionSink;
        for (UUID viewerId : viewerIds) {
            audiences.onOwner(viewerId, ignored -> {
                try {
                    sink.publish(subjectId, viewerId, vanished);
                } catch (RuntimeException | LinkageError exception) {
                    plugin.getLogger().log(
                            Level.WARNING,
                            "RoseChat vanish presence transition failed for viewer " + viewerId,
                            exception
                    );
                }
            });
        }
    }

    private void reconcileLiveRank(Player player) {
        UUID playerId = player.getUniqueId();
        StaffRank cachedRank = onlineStaffRanks.get(playerId);
        StaffRank liveRank = resolveLiveRank(player);
        reconcileViewerAuthority(player, playerId, cachedRank, liveRank);
        if (stateWrites.contains(playerId)) {
            return;
        }
        StaffRank durableRank = durableVanishedRanks.get(playerId);
        boolean vanished = visibility.isVanished(playerId);
        VanishRankReconciliationPolicy.StaffModeState staffModeState = staffModeState(playerId);
        VanishRankReconciliationPolicy.VanishAction action = VanishRankReconciliationPolicy.vanishAction(
                vanished,
                durableRank,
                liveRank,
                staffModeState
        );
        applyVanishAction(player, action, cachedRank, liveRank, durableRank, vanished);
        reconcileVanishGameMode(player);
    }

    private void reconcileViewerAuthority(
            Player player,
            UUID playerId,
            StaffRank cachedRank,
            StaffRank liveRank
    ) {
        VanishRankReconciliationPolicy.ViewerAction action =
                VanishRankReconciliationPolicy.viewerAction(cachedRank, liveRank);
        if (!applyViewerAction(playerId, liveRank, action)) {
            return;
        }
        applySpectatorPolicy(player, player.getGameMode(), false);
        audiences.updateGameMode(playerId, player.getGameMode());
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
    }

    private void applyVanishAction(
            Player player,
            VanishRankReconciliationPolicy.VanishAction action,
            StaffRank cachedRank,
            StaffRank liveRank,
            StaffRank durableRank,
            boolean vanished
    ) {
        UUID playerId = player.getUniqueId();
        switch (action) {
            case UPDATE_RANK -> updateVanishedRank(player, liveRank);
            case VERIFY_SESSION -> verifyLowerRankSession(player, liveRank);
            case DISABLE -> disableReconciledVanish(player, cachedRank, liveRank, durableRank);
            case NONE -> clearCompletedPendingExit(playerId, vanished, durableRank);
            default -> throw new IllegalStateException("Unsupported vanish action: " + action);
        }
    }

    private void updateVanishedRank(Player player, StaffRank liveRank) {
        applyReconciledMemoryState(player, liveRank, true);
        reconcileDurableState(
                player.getUniqueId(),
                liveRank,
                true,
                "Your vanish visibility was updated for your current staff rank."
        );
    }

    private void verifyLowerRankSession(Player player, StaffRank liveRank) {
        UUID playerId = player.getUniqueId();
        if (liveRank != null && visibility.vanishedRank(playerId) != liveRank) {
            applyReconciledMemoryState(player, liveRank, true);
        }
        verifyStaffSession(playerId);
    }

    private void disableReconciledVanish(
            Player player,
            StaffRank cachedRank,
            StaffRank liveRank,
            StaffRank durableRank
    ) {
        UUID playerId = player.getUniqueId();
        StaffRank writeRank = firstPlayerRank(
                durableRank,
                visibility.vanishedRank(playerId),
                cachedRank,
                liveRank
        );
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

    private void clearCompletedPendingExit(UUID playerId, boolean vanished, StaffRank durableRank) {
        if (pendingStaffModeExitDisables.contains(playerId) && !vanished && durableRank == null) {
            pendingStaffModeExitDisables.remove(playerId);
        }
    }

    private VanishRankReconciliationPolicy.StaffModeState staffModeState(UUID playerId) {
        if (staffMode.active(playerId)) {
            durableStaffSessionPresence.put(playerId, true);
            pendingStaffModeExitDisables.remove(playerId);
            return VanishRankReconciliationPolicy.StaffModeState.ACTIVE;
        }
        if (pendingStaffModeExitDisables.contains(playerId)) {
            return VanishRankReconciliationPolicy.StaffModeState.EXITED;
        }
        Boolean durablePresence = durableStaffSessionPresence.get(playerId);
        if (durablePresence == null) {
            return VanishRankReconciliationPolicy.StaffModeState.UNKNOWN;
        }
        return durablePresence
                ? VanishRankReconciliationPolicy.StaffModeState.ACTIVE
                : VanishRankReconciliationPolicy.StaffModeState.INACTIVE;
    }

    private void verifyStaffSession(UUID playerId) {
        Instant retryAfter = staffSessionCheckRetryAfter.get(playerId);
        if (retryAfter != null && clock.instant().isBefore(retryAfter)) {
            return;
        }
        UUID token = UUID.randomUUID();
        if (pendingStaffSessionChecks.putIfAbsent(playerId, token) != null) {
            return;
        }
        if (!submit(() -> {
            try {
                StaffSessionStore loaded = sessions.get();
                if (loaded == null) {
                    throw new IllegalStateException("staff session storage is not ready");
                }
                boolean present = loaded.active(playerId).isPresent();
                audiences.onOwner(
                        playerId,
                        player -> completeStaffSessionCheck(playerId, token, present, player),
                        () -> retireStaffSessionCheck(playerId, token)
                );
            } catch (RuntimeException exception) {
                staffSessionCheckFailed(playerId, token, exception);
            }
        })) {
            staffSessionCheckFailed(
                    playerId,
                    token,
                    new RejectedExecutionException("bounded staff-session verification queue is full")
            );
        }
    }

    private void completeStaffSessionCheck(UUID playerId, UUID token, boolean present, Player player) {
        if (!pendingStaffSessionChecks.remove(playerId, token)) {
            return;
        }
        durableStaffSessionPresence.put(playerId, present);
        staffSessionCheckRetryAfter.remove(playerId);
        staffSessionCheckFailureNotified.remove(playerId);
        reconcileLiveRank(player);
    }

    private void retireStaffSessionCheck(UUID playerId, UUID token) {
        if (pendingStaffSessionChecks.remove(playerId, token)) {
            durableStaffSessionPresence.remove(playerId);
        }
    }

    private void staffSessionCheckFailed(UUID playerId, UUID token, RuntimeException exception) {
        if (!pendingStaffSessionChecks.remove(playerId, token)) {
            return;
        }
        staffSessionCheckRetryAfter.put(playerId, clock.instant().plusSeconds(RECONCILIATION_RETRY_SECONDS));
        plugin.getLogger().log(Level.SEVERE, "Vanish staff-session verification failed; retry remains pending", exception);
        if (staffSessionCheckFailureNotified.add(playerId)) {
            message(
                    playerId,
                    "Vanish could not verify your staff session; visibility remains unchanged and will retry."
            );
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
            selectedGameModes.computeIfAbsent(playerId, ignored -> selectedGameModeForEnable(player, rank));
            hiddenSpectators.remove(playerId);
            reconcileVanishedGameMode(player);
        } else {
            if (pendingStaffModeExitDisables.contains(playerId)) {
                selectedGameModes.remove(playerId);
            } else {
                restoreSelectedGameMode(player);
            }
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
                persistState(loaded, playerId, rank, vanished, selectedGameModes.get(playerId));
                if (vanished) {
                    durableVanishedRanks.put(playerId, rank);
                } else {
                    durableVanishedRanks.remove(playerId);
                    pendingStaffModeExitDisables.remove(playerId);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        boolean restoringStaffState = staffMode.restoringSavedState(playerId);
        if (visibility.isVanished(playerId)
                && !restoringStaffState
                && !vanishGameModeApplications.contains(playerId)) {
            StaffRank rank = resolveLiveRank(player);
            if (isSelectableGameMode(rank, event.getNewGameMode())) {
                selectedGameModes.put(playerId, event.getNewGameMode());
                persistSelectedGameMode(playerId, rank, event.getNewGameMode());
            }
            if (!isSelectableGameMode(rank, event.getNewGameMode())) {
                event.setCancelled(true);
                return;
            }
        }
        boolean viewerChanged = recordViewerRank(player);
        applySpectatorPolicy(player, event.getNewGameMode(), true);
        audiences.updateGameMode(playerId, event.getNewGameMode());
        if (viewerChanged) {
            audiences.refreshViewer(playerId);
        }
        audiences.refreshTarget(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        audiences.register(playerId, player, player.getGameMode());
        recordViewerRank(player);
        applySpectatorPolicy(player, player.getGameMode(), true);
        reconcileLiveRank(player);
        reconcileVanishGameMode(player);
        refreshDurableVanish(playerId);
        if (visibility.isVanished(playerId)) {
            event.joinMessage(null);
        }
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (visibility.isVanished(player.getUniqueId())) {
            event.quitMessage(null);
        }
        UUID playerId = player.getUniqueId();
        audiences.remove(playerId);
        visibility.removeViewer(playerId);
        onlineStaffRanks.remove(playerId);
        durableStaffSessionPresence.remove(playerId);
        pendingStaffSessionChecks.remove(playerId);
        staffSessionCheckRetryAfter.remove(playerId);
        staffSessionCheckFailureNotified.remove(playerId);
        hiddenSpectators.remove(playerId);
        pendingRankChecks.remove(playerId);
        pendingDurableLoads.remove(playerId);
        pendingStaffModeExitDisables.remove(playerId);
        reconciliationRetryAfter.remove(playerId);
        reconciliationFailureNotified.remove(playerId);
        selectedModeWrites.remove(playerId);
        vanishGameModeApplications.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin) || event.getPlugin().getName().equals("ProtocolLib")) {
            spectatorTabPackets.close();
            silentContainerPackets.close();
            if (!event.getPlugin().equals(plugin)) {
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
                hiddenSpectators.contains(targetId) && !visibility.isVanished(targetId),
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

    private SilentContainerPacketAdapter installSilentContainerPackets() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning(
                    "ProtocolLib is unavailable; vanished container opens will animate normally"
            );
            return SilentContainerPacketAdapter.unavailable();
        }
        try {
            return ProtocolLibSilentContainerPacketAdapter.install(
                    plugin, silentContainers, clock, this::silentContainerPacketsFailed);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "ProtocolLib silent-container adapter could not start; container animations will not be suppressed",
                    failure
            );
            return SilentContainerPacketAdapter.unavailable();
        }
    }

    private void silentContainerPacketsFailed() {
        plugin.getLogger().warning(
                "Silent-container packet adapter disabled after failure; container animations will play normally");
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
                    id -> hiddenSpectators.contains(id) && !visibility.isVanished(id),
                    visibility::isVanished
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

    private void refreshDurableVanish(UUID playerId) {
        if (!pendingDurableLoads.add(playerId)) {
            return;
        }
        if (!submit(() -> {
            try {
                VanishStore loaded = store.get();
                if (loaded == null) {
                    return;
                }
                java.util.Optional<VanishRecord> record = loaded.active(playerId);
                audiences.onOwner(
                        playerId,
                        player -> {
                            try {
                                applyDurableVanishRecovery(player, record.orElse(null));
                            } finally {
                                pendingDurableLoads.remove(playerId);
                            }
                        },
                        () -> pendingDurableLoads.remove(playerId)
                );
            } catch (RuntimeException exception) {
                pendingDurableLoads.remove(playerId);
                plugin.getLogger().log(Level.WARNING, "Durable vanish recovery failed", exception);
            }
        })) {
            pendingDurableLoads.remove(playerId);
        }
    }

    private void applyDurableVanishRecovery(Player player, VanishRecord record) {
        UUID playerId = player.getUniqueId();
        if (record == null) {
            boolean wasVanished = visibility.isVanished(playerId);
            durableVanishedRanks.remove(playerId);
            visibility.setVanished(playerId, null, false);
            if (wasVanished && selectedGameModes.containsKey(playerId)) {
                restoreSelectedGameMode(player);
            } else {
                selectedGameModes.remove(playerId);
            }
            applySpectatorPolicy(player, player.getGameMode(), false);
            audiences.updateGameMode(playerId, player.getGameMode());
            audiences.refreshTarget(playerId);
            return;
        }
        durableVanishedRanks.put(playerId, record.rank());
        rememberPersistedGameMode(record);
        visibility.setVanished(playerId, record.rank(), true);
        if (!staffMode.transitioning(playerId)) {
            reconcileVanishedGameMode(player);
        }
        audiences.updateGameMode(playerId, player.getGameMode());
        audiences.refreshViewer(playerId);
        audiences.refreshTarget(playerId);
    }

    private void rememberPersistedGameMode(VanishRecord record) {
        String selected = record.selectedGameMode();
        if (selected == null) {
            return;
        }
        try {
            selectedGameModes.put(record.staffId(), GameMode.valueOf(selected));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Ignoring invalid persisted vanish selected game mode for " + record.staffId());
        }
    }

    private void reconcileVanishGameMode(Player player) {
        UUID playerId = player.getUniqueId();
        if (!visibility.isVanished(playerId) || staffMode.transitioning(playerId)) {
            return;
        }
        StaffRank rank = onlineStaffRanks.get(playerId);
        if (!selectedGameModes.containsKey(playerId)) {
            GameMode selected = selectedGameModeForEnable(player, rank);
            selectedGameModes.put(playerId, selected);
            persistSelectedGameMode(playerId, rank, selected);
        }
        reconcileVanishedGameMode(player);
    }

    private GameMode selectedGameModeForEnable(Player player, StaffRank rank) {
        GameMode current = player.getGameMode();
        return isSelectableGameMode(rank, current) ? current : defaultSelectedGameMode(rank);
    }

    private static GameMode defaultSelectedGameMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER
                ? GameMode.CREATIVE
                : GameMode.SPECTATOR;
    }

    private static boolean isSelectableGameMode(StaffRank rank, GameMode mode) {
        if (rank == StaffRank.DEVELOPER || rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER) {
            return true;
        }
        if (rank == StaffRank.HELPER || rank == StaffRank.MOD) {
            return mode == GameMode.SURVIVAL || mode == GameMode.SPECTATOR;
        }
        return rank == StaffRank.SYSTEM && mode == GameMode.SPECTATOR;
    }

    private void reconcileVanishedGameMode(Player player) {
        StaffRank rank = resolveLiveRank(player);
        UUID playerId = player.getUniqueId();
        GameMode selected = selectedGameModes.getOrDefault(
                playerId,
                selectedGameModeForEnable(player, rank)
        );
        if (!isSelectableGameMode(rank, selected)) {
            selected = defaultSelectedGameMode(rank);
        }
        if (player.getGameMode() == selected) {
            return;
        }
        vanishGameModeApplications.add(playerId);
        try {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            player.setGameMode(selected);
            if (player.getGameMode() != selected) {
                throw new IllegalStateException("vanished selected game mode transition was rejected");
            }
        } finally {
            vanishGameModeApplications.remove(playerId);
        }
    }

    private void restoreSelectedGameMode(Player player) {
        UUID playerId = player.getUniqueId();
        StaffRank rank = resolveLiveRank(player);
        GameMode selected = selectedGameModes.getOrDefault(playerId, defaultSelectedGameMode(rank));
        if (!isSelectableGameMode(rank, selected)) {
            selected = defaultSelectedGameMode(rank);
        }
        vanishGameModeApplications.add(playerId);
        try {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            player.setGameMode(selected);
            if (player.getGameMode() != selected) {
                throw new IllegalStateException("selected game mode restoration was rejected");
            }
            selectedGameModes.remove(playerId);
        } finally {
            vanishGameModeApplications.remove(playerId);
        }
    }

    private void persistSelectedGameMode(UUID playerId, StaffRank rank, GameMode selected) {
        if (rank == null || !selectedModeWrites.add(playerId)) {
            return;
        }
        if (!submit(() -> {
            try {
                VanishStore loaded = store.get();
                if (loaded == null || !visibility.isVanished(playerId)) {
                    return;
                }
                VanishStore.WriteResult result = loaded.set(
                        playerId,
                        rank,
                        true,
                        playerId,
                        clock.instant(),
                        staffMode.active(playerId),
                        selected.name()
                );
                if (result == VanishStore.WriteResult.STAFF_SESSION_NOT_ACTIVE) {
                    throw new IllegalStateException("active staff session ended before selected mode commit");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vanish selected game mode persistence failed", exception);
            } finally {
                selectedModeWrites.remove(playerId);
            }
        })) {
            selectedModeWrites.remove(playerId);
        }
    }

    @FunctionalInterface
    public interface PresenceTransitionSink {
        void publish(UUID subjectId, UUID viewerId, boolean vanished);
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
        audiences.onOwner(playerId, player -> player.sendMessage(StaffMessageStyle.style(Component.text(message))));
    }

    private boolean onEntity(Player player, Runnable operation) {
        return player.getScheduler().execute(plugin, operation, null, 1L);
    }
}
