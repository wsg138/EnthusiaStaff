package net.enthusia.staff.paper.staff;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionOwnership;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffModeManager implements Listener {
    private static final String RANK_REMOVED_MESSAGE =
            "Your explicit staff rank is no longer assigned; restoring your saved state.";
    private final JavaPlugin plugin;
    private final Clock clock;
    private final Instant runtimeStartedAt;
    private final String serverId;
    private final Supplier<StaffSessionStore> store;
    private final ExecutorService workers;
    private final StaffStateCodec codec = new StaffStateCodec();
    private final CombatStatusAdapter combat;
    private final NamespacedKey staffToolKey;
    private final NamespacedKey staffToolOwnerKey;
    private final NamespacedKey staffToolSessionKey;
    private final Map<UUID, StaffSessionSnapshot> active = new ConcurrentHashMap<>();
    private final Map<UUID, StaffSessionSnapshot> pendingLocalSessions = new ConcurrentHashMap<>();
    private final Map<UUID, StaffRank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, String> toolSessions = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> transitions = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> handoffGaps = ConcurrentHashMap.newKeySet();
    private final StaffModeRecoveryGate recoveryGate = new StaffModeRecoveryGate(transitions);
    private final java.util.Set<UUID> profileApplications = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> snapshotRestorations = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> pendingRankChecks = ConcurrentHashMap.newKeySet();
    private final StaffModeHandoffIntentRegistry handoffResumes;
    private final StaffModeSourceHandoffRegistry sourceHandoffs = new StaffModeSourceHandoffRegistry();
    private final StaffModeActivationCoordinator activation;
    private final StaffToolLayout toolLayout;
    private final AtomicBoolean rankReconciliationStarted = new AtomicBoolean();
    private volatile Consumer<UUID> exitListener = ignored -> {
    };
    private volatile Consumer<StaffSessionSnapshot> activeSessionListener = ignored -> {
    };
    private volatile Consumer<UUID> gameModeTransitionGuardBegin = ignored -> {
    };
    private volatile Consumer<UUID> gameModeTransitionGuardEnd = ignored -> {
    };
    private volatile net.enthusia.staff.paper.audit.StaffActionLogger actionLogger;
    private volatile java.util.function.Function<UUID, Boolean> vanishedLookup = id -> false;
    private volatile Consumer<Player> entryListener = ignored -> { };

    public StaffModeManager(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<StaffSessionStore> store,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.toolLayout = StaffToolLayout.load(plugin.getConfig());
        this.clock = clock;
        this.runtimeStartedAt = clock.instant();
        this.serverId = serverId;
        this.store = store;
        this.workers = workers;
        this.handoffResumes = new StaffModeHandoffIntentRegistry(clock);
        this.combat = new CombatStatusAdapter(plugin);
        this.staffToolKey = new NamespacedKey(plugin, "staff_tool");
        this.staffToolOwnerKey = new NamespacedKey(plugin, "staff_tool_owner");
        this.staffToolSessionKey = new NamespacedKey(plugin, "staff_tool_session");
        this.activation = new StaffModeActivationCoordinator(
                clock,
                workers,
                plugin.getLogger(),
                active,
                ranks,
                transitions
        );
    }

    public boolean active(UUID playerId) {
        return active.containsKey(playerId) || handoffGaps.contains(playerId);
    }

    public UUID activeSessionId(UUID playerId) {
        StaffSessionSnapshot session = active.get(playerId);
        return session == null ? null : session.sessionId();
    }

    public boolean transitioning(UUID playerId) {
        return playerId != null && transitions.contains(playerId);
    }

    public boolean authorityActive(UUID playerId) {
        return playerId != null
                && active.containsKey(playerId)
                && !transitions.contains(playerId);
    }

    /**
     * Returns whether the currently applied Staff Mode profile is the Helper observer profile.
     *
     * <p>This deliberately reads the session's cached rank instead of live permissions so Helper
     * protections remain fail-closed while a rank removal/change is being reconciled. The backing
     * maps are concurrent, so callers may safely use this from another entity scheduler.</p>
     */
    boolean helperObserverActive(UUID playerId) {
        return playerId != null
                && active.containsKey(playerId)
                && ranks.get(playerId) == StaffRank.HELPER;
    }

    /**
     * Returns the authoritative Staff Mode session rank, or null if the player
     * has no active session or the rank is currently being reconciled.
     * Callers must treat null as fail-closed while Staff Mode is active.
     */
    public StaffRank sessionRank(UUID playerId) {
        return playerId == null ? null : ranks.get(playerId);
    }

    public CombatStatusAdapter combat() {
        return combat;
    }

    public boolean restoringSavedState(UUID playerId) {
        return snapshotRestorations.contains(playerId);
    }

    public void setExitListener(Consumer<UUID> exitListener) {
        this.exitListener = java.util.Objects.requireNonNull(exitListener);
    }

    public void setActiveSessionListener(Consumer<StaffSessionSnapshot> listener) {
        activeSessionListener = java.util.Objects.requireNonNull(listener);
    }

    /**
     * Registers the vanish game-mode guard so staff-mode profile transitions are recognized as
     * plugin-initiated instead of being cancelled (C1: entering staff mode while vanished as
     * ADMIN/FOUNDER previously deadlocked on the vanish listener's gamemode cancellation).
     */
    public void setGameModeTransitionGuard(Consumer<UUID> begin, Consumer<UUID> end) {
        this.gameModeTransitionGuardBegin = java.util.Objects.requireNonNull(begin, "begin");
        this.gameModeTransitionGuardEnd = java.util.Objects.requireNonNull(end, "end");
    }

    /** Installs the staff-action audit logger (overnight permission model: tiered allow+log). */
    public void setActionLogger(net.enthusia.staff.paper.audit.StaffActionLogger actionLogger) {
        this.actionLogger = actionLogger;
    }

    /** Lets the manager read vanish state for audit lines without depending on VanishManager. */
    public void setVanishedLookup(java.util.function.Function<UUID, Boolean> vanishedLookup) {
        this.vanishedLookup = java.util.Objects.requireNonNull(vanishedLookup, "vanishedLookup");
    }

    /** Fresh entry only: handoffs and recovery preserve their existing visibility choice. */
    public void setEntryListener(Consumer<Player> entryListener) {
        this.entryListener = java.util.Objects.requireNonNull(entryListener, "entryListener");
    }

    /**
     * Resolves the caller's on-duty tier, or {@code null} while a transition is in progress or
     * the rank cannot be resolved (fail-closed callers must block).
     */
    public StaffDutyTier dutyTier(Player player) {
        return StaffDutyTier.of(rankForAction(player));
    }

    private void audit(Player player, StaffRank rank, String action, String detail) {
        net.enthusia.staff.paper.audit.StaffActionLogger logger = actionLogger;
        if (logger == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        boolean vanished;
        try {
            vanished = vanishedLookup.apply(playerId);
        } catch (RuntimeException exception) {
            vanished = false;
        }
        boolean onDuty = authorityActive(playerId);
        logger.log(playerId, playerName, rank, vanished, onDuty, action, detail);
    }

    /**
     * Writes one staff-action audit line for an allowed on-duty world/inventory interaction
     * (Mod logged-not-blocked, Admin/Founder unrestricted-but-logged). Never throws.
     */
    public void logStaffAction(Player player, String action, String detail) {
        try {
            audit(player, rankForAction(player), action, detail);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Staff action audit failed for " + action, exception);
        }
    }

    private static String describe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "empty";
        }
        return item.getType() + "x" + item.getAmount();
    }

    public boolean prepareBackendHandoffResume(UUID playerId, UUID transferId) {
        if (!handoffResumes.prepare(playerId, transferId)) {
            return false;
        }
        handoffGaps.add(playerId);
        return true;
    }

    public boolean cancelBackendHandoffResume(UUID playerId, UUID transferId) {
        handoffResumes.cancel(playerId, transferId);
        abandonHandoffGap(playerId);
        return true;
    }

    public CompletableFuture<Boolean> rollbackBackendHandoff(UUID playerId, UUID transferId) {
        handoffResumes.cancel(playerId, transferId);
        handoffGaps.add(playerId);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        onEntity(playerId, player -> {
            StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
            if (rank == null) {
                abandonHandoffGap(playerId);
                result.complete(false);
                return;
            }
            enter(player, rank);
            result.complete(active.containsKey(playerId) || transitions.contains(playerId));
        }, () -> {
            abandonHandoffGap(playerId);
            result.complete(false);
        });
        return result;
    }

    public CompletableFuture<Boolean> closeForBackendHandoff(
            UUID playerId,
            UUID expectedSessionId,
            long expectedRevision,
            UUID transferId
    ) {
        StaffSessionSnapshot runtime = active.get(playerId);
        if (transferId == null || !sourceHandoffs.begin(playerId, transferId)) {
            return CompletableFuture.completedFuture(false);
        }
        if (!validHandoffSource(runtime, expectedSessionId, expectedRevision)
                || !transitions.add(playerId)) {
            sourceHandoffs.finish(playerId, transferId);
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!submit(() -> beginBackendHandoffClose(
                playerId, expectedSessionId, expectedRevision, transferId, result))) {
            sourceHandoffs.finish(playerId, transferId);
            transitions.remove(playerId);
            result.complete(false);
        }
        return result;
    }

    public boolean abortBackendHandoffSource(UUID playerId, UUID transferId) {
        return sourceHandoffs.abort(playerId, transferId);
    }

    public void startRankReconciliation() {
        if (!rankReconciliationStarted.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (UUID playerId : active.keySet()) {
                if (!pendingRankChecks.add(playerId)) {
                    continue;
                }
                onEntity(
                        playerId,
                        player -> {
                            try {
                                reconcileActiveRank(player);
                            } finally {
                                pendingRankChecks.remove(playerId);
                            }
                        },
                        () -> pendingRankChecks.remove(playerId)
                );
            }
        }, 20L, 20L);
    }

    public void enter(Player player, StaffRank rank) {
        java.util.Objects.requireNonNull(rank, "rank");
        UUID playerId = player.getUniqueId();
        if (!transitions.add(playerId)) {
            player.sendMessage(StaffMessageStyle.style(Component.text("A staff-mode transition is already in progress.")));
            return;
        }
        CombatStatusAdapter.Status combatStatus = combat.status(player);
        if (combatStatus != CombatStatusAdapter.Status.CLEAR) {
            failEntry(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text(combatStatus == CombatStatusAdapter.Status.TAGGED
                    ? "You cannot enter staff mode while combat tagged."
                    : "Combat state could not be verified; staff mode entry failed safely.")));
            return;
        }
        StaffStateCodec.Captured captured;
        try {
            captured = codec.capture(player, serverId);
        } catch (RuntimeException exception) {
            failEntry(playerId);
            plugin.getLogger().log(Level.SEVERE, "Staff state snapshot capture failed", exception);
            player.sendMessage(StaffMessageStyle.style(Component.text("Your state could not be snapshotted; staff mode was not entered.")));
            return;
        }
        if (!submit(() -> {
            StaffSessionStore loaded = store.get();
            if (loaded == null) {
                failEntry(playerId);
                message(playerId, "Staff session storage is not ready; your inventory was not changed.");
                return;
            }
            try {
                StaffSessionSnapshot session = loaded.begin(
                        playerId, serverId, captured.schemaVersion(), captured.checksum(),
                        captured.snapshot(), clock.instant()
                );
                if (!serverId.equals(session.serverId()) || session.state() != StaffSessionState.ACTIVE) {
                    throw new IllegalStateException(
                            "staff session entry returned a snapshot not actively owned by this backend"
                    );
                }
                pendingLocalSessions.put(playerId, session);
                onEntity(
                        playerId,
                        current -> activateFreshSession(
                                playerId,
                                session,
                                loaded,
                                current,
                                rank
                        ),
                        () -> detachUnappliedLease(playerId, session, loaded, captured.checksum())
                );
            } catch (RuntimeException exception) {
                failEntry(playerId);
                plugin.getLogger().log(Level.SEVERE, "Staff session entry failed", exception);
                message(playerId, "Staff mode entry failed before your inventory was changed.");
            }
        })) {
            failEntry(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text("The bounded work queue is full; staff mode was not entered.")));
        }
    }

    private void activateFreshSession(UUID playerId, StaffSessionSnapshot session, StaffSessionStore loaded,
            Player player, StaffRank rank) {
        activateDurableSession(playerId, session, loaded, player, rank,
                StaffModeActivationCoordinator.ActivationPath.INITIAL_ENTRY,
                "Staff mode entered after durable snapshot commit; enabling vanish.");
        if (active.get(playerId) == session) {
            entryListener.accept(player);
        }
    }

    public void exit(Player player) {
        UUID playerId = player.getUniqueId();
        StaffSessionSnapshot localSession = active.get(playerId);
        if (localSession == null
                || localSession.state() != StaffSessionState.ACTIVE
                || !serverId.equalsIgnoreCase(localSession.serverId())) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Staff Mode is still resuming on this backend; try the command again shortly."
            )));
            return;
        }
        if (!transitions.add(playerId)) {
            player.sendMessage(StaffMessageStyle.style(Component.text("A staff-mode transition is already in progress.")));
            return;
        }
        beginDurableExit(playerId, "Staff mode exit");
    }

    // Handoff resume must snapshot the destination backend's native player state before
    // transferred vanish state is applied later in the same join event.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (handoffResumes.consume(playerId).isPresent()) {
            StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
            if (rank == null) {
                abandonHandoffGap(playerId);
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        "Staff Mode could not resume because your explicit staff rank is unavailable."
                )));
                return;
            }
            // PREPARE is only a fast-path hint. The source detach may still be committing,
            // so use durable recovery/rebind instead of a one-shot new Staff entry.
            recover(playerId, rank);
            return;
        }
        if (handoffGaps.contains(playerId)) {
            abandonHandoffGap(playerId);
        }
        recover(player);
    }

    public void recover(Player player) {
        recover(
                player.getUniqueId(),
                PaperStaffRankResolver.resolve(player::hasPermission).orElse(null)
        );
    }

    public void recover(UUID playerId, StaffRank rankSnapshot) {
        if (!recoveryGate.begin(playerId)) {
            return;
        }
        if (!submit(() -> {
            StaffSessionStore loaded = store.get();
            if (loaded == null) {
                recoveryGate.retry(playerId);
                message(
                        playerId,
                        "Staff session storage is not ready; interaction remains blocked until startup recovery retries."
                );
                return;
            }
            try {
                StaffSessionSnapshot session = loaded.active(playerId).orElse(null);
                if (session == null) {
                    recoveryGate.clear(playerId);
                    if (handoffGaps.contains(playerId) && rankSnapshot != null) {
                        onEntity(
                                playerId,
                                current -> enter(current, rankSnapshot),
                                () -> abandonHandoffGap(playerId)
                        );
                    }
                    return;
                }
                if (StaffSessionOwnership.detached(session.serverId())) {
                    onEntity(playerId, current -> resumeDetachedSession(playerId, loaded, current));
                    return;
                }
                if (!session.serverId().equalsIgnoreCase(serverId)) {
                    // A backend switch can race the source's quit/detach write. Do not turn
                    // that normal race into RECOVERY_REQUIRED or block the destination.
                    recoveryGate.retry(playerId);
                    scheduleRecoveryRetry(playerId);
                    return;
                }
                pendingLocalSessions.put(playerId, session);
                if (staleFromPriorRuntime(session)) {
                    recoverPriorRuntimeSession(playerId, session, loaded);
                    return;
                }
                if (session.state() == StaffSessionState.EXITING
                        || session.state() == StaffSessionState.RECOVERY_REQUIRED) {
                    StaffSessionSnapshot restoring = session.state() == StaffSessionState.RECOVERY_REQUIRED
                            ? loaded.beginExit(playerId, clock.instant()).orElseThrow(() ->
                                    new IllegalStateException("recovery-required staff session disappeared during exit"))
                            : session;
                    restoreAndVerify(playerId, restoring, loaded);
                    return;
                }
                if (StaffModeRankReconciliationPolicy.decide(null, rankSnapshot)
                        == StaffModeRankReconciliationPolicy.Action.EXIT_SESSION) {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElseThrow(() ->
                            new IllegalStateException("active staff session disappeared during rank-removal exit"));
                    message(playerId, RANK_REMOVED_MESSAGE);
                    restoreAndVerify(playerId, exiting, loaded);
                    return;
                }
                onEntity(playerId, current -> finishActiveRecovery(playerId, session, loaded, current));
            } catch (RuntimeException exception) {
                recoveryGate.retry(playerId);
                plugin.getLogger().log(Level.SEVERE, "Staff session recovery failed", exception);
                message(playerId, "Your staff session could not be recovered automatically; contact an administrator.");
            }
        })) {
            recoveryGate.retry(playerId);
            message(playerId, "The bounded work queue is full; staff session recovery did not start.");
        }
    }

    private void resumeDetachedSession(
            UUID playerId,
            StaffSessionStore loaded,
            Player player
    ) {
        StaffRank rank = resolveDetachedRank(playerId, player);
        if (rank == null) {
            return;
        }
        StaffStateCodec.Captured captured = captureDetachedState(playerId, player);
        if (captured == null) {
            return;
        }
        if (!submit(() -> rebindDetachedSession(playerId, loaded, rank, captured))) {
            recoveryGate.retry(playerId);
        }
    }

    private StaffRank resolveDetachedRank(UUID playerId, Player player) {
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            recoveryGate.retry(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Your Staff Mode session is still active, but your staff rank is unavailable."
            )));
        }
        return rank;
    }

    private StaffStateCodec.Captured captureDetachedState(UUID playerId, Player player) {
        try {
            return codec.capture(player, serverId);
        } catch (RuntimeException exception) {
            recoveryGate.retry(playerId);
            plugin.getLogger().log(Level.SEVERE, "Detached Staff Mode state capture failed", exception);
            return null;
        }
    }

    private void rebindDetachedSession(
            UUID playerId,
            StaffSessionStore loaded,
            StaffRank rank,
            StaffStateCodec.Captured captured
    ) {
        try {
            StaffSessionSnapshot rebound = loaded.begin(
                    playerId,
                    serverId,
                    captured.schemaVersion(),
                    captured.checksum(),
                    captured.snapshot(),
                    clock.instant()
            );
            validateDetachedRebind(rebound);
            pendingLocalSessions.put(playerId, rebound);
            onEntity(
                    playerId,
                    current -> activateDurableSession(
                            playerId,
                            rebound,
                            loaded,
                            current,
                            rank,
                            StaffModeActivationCoordinator.ActivationPath.INITIAL_ENTRY,
                            "Your network Staff Mode session resumed on this backend."
                    ),
                    () -> detachUnappliedLease(playerId, rebound, loaded, captured.checksum())
            );
        } catch (RuntimeException exception) {
            recoveryGate.retry(playerId);
            plugin.getLogger().log(Level.SEVERE, "Detached Staff Mode rebind failed", exception);
            scheduleRecoveryRetry(playerId);
        }
    }

    private void validateDetachedRebind(StaffSessionSnapshot rebound) {
        if (!serverId.equalsIgnoreCase(rebound.serverId())
                || rebound.state() != StaffSessionState.ACTIVE) {
            throw new IllegalStateException("detached Staff Mode session did not rebind to this backend");
        }
    }

    private void scheduleRecoveryRetry(UUID playerId) {
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, ignored -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                recover(player);
            }
        }, 10L);
    }

    private boolean staleFromPriorRuntime(StaffSessionSnapshot session) {
        return session.state() == StaffSessionState.ACTIVE && session.startedAt().isBefore(runtimeStartedAt);
    }

    private void recoverPriorRuntimeSession(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded
    ) {
        // A backend restart is not an intentional Staff Mode exit. Recover the backend-local
        // native state, detach the stale lease, then immediately capture this runtime's native
        // state and reapply the still-active network Staff Mode session.
        onEntity(playerId, player -> {
            try {
                if (!restoreSavedState(player, session)) {
                    recoveryGate.retry(playerId);
                    return;
                }
                String checksum = codec.verifiedRestorationChecksum(
                        player, session.serverId(), session.snapshot(), session.checksum());
                if (!submit(() -> {
                    try {
                        if (loaded.detach(
                                playerId,
                                session.sessionId(),
                                session.revision(),
                                session.serverId(),
                                checksum,
                                clock.instant()
                        ).isEmpty()) {
                            recoveryGate.retry(playerId);
                            scheduleRecoveryRetry(playerId);
                            return;
                        }
                        onEntity(playerId, current -> resumeDetachedSession(playerId, loaded, current));
                    } catch (RuntimeException exception) {
                        recoveryGate.retry(playerId);
                        plugin.getLogger().log(Level.SEVERE, "Staff Mode restart rebind failed", exception);
                        scheduleRecoveryRetry(playerId);
                    }
                })) {
                    recoveryGate.retry(playerId);
                }
            } catch (RuntimeException exception) {
                recoveryGate.retry(playerId);
                plugin.getLogger().log(Level.SEVERE, "Staff Mode restart restoration failed", exception);
            }
        }, () -> recoveryGate.retry(playerId));
    }

    private void finishActiveRecovery(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            Player player
    ) {
        StaffRank currentRank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (StaffModeRankReconciliationPolicy.decide(null, currentRank)
                == StaffModeRankReconciliationPolicy.Action.EXIT_SESSION) {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    RANK_REMOVED_MESSAGE
            )));
            if (!submit(() -> {
                try {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElseThrow(() ->
                            new IllegalStateException("active staff session disappeared during recovery exit"));
                    restoreAndVerify(playerId, exiting, loaded);
                } catch (RuntimeException exception) {
                    recoveryGate.retry(playerId);
                    plugin.getLogger().log(Level.SEVERE, "Staff session recovery exit failed", exception);
                    message(playerId, "Your staff session could not be recovered automatically; contact an administrator.");
                }
            })) {
                recoveryGate.retry(playerId);
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        "The bounded work queue is full; staff session recovery did not continue."
                )));
            }
            return;
        }
        activateDurableSession(
                playerId,
                session,
                loaded,
                player,
                currentRank,
                StaffModeActivationCoordinator.ActivationPath.ACTIVE_RECOVERY,
                "Your active staff session was resumed."
        );
    }

    private void activateDurableSession(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            Player player,
            StaffRank rank,
            StaffModeActivationCoordinator.ActivationPath path,
            String successMessage
    ) {
        boolean activated = activation.activate(
                playerId,
                session,
                loaded,
                rank,
                path,
                () -> applyStaffState(
                        player,
                        rank,
                        path == StaffModeActivationCoordinator.ActivationPath.ACTIVE_RECOVERY
                ),
                () -> {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElseThrow(() ->
                            new IllegalStateException("staff session disappeared during activation rollback"));
                    restoreAndVerify(playerId, exiting, loaded);
                },
                message -> player.sendMessage(StaffMessageStyle.style(Component.text(message))),
                successMessage
        );
        if (!activated) {
            toolSessions.remove(playerId);
            return;
        }
        pendingLocalSessions.remove(playerId, session);
        handoffGaps.remove(playerId);
        try {
            activeSessionListener.accept(session);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Staff active-session callback failed", exception);
        }
    }

    private boolean validHandoffSource(
            StaffSessionSnapshot session,
            UUID expectedSessionId,
            long expectedRevision
    ) {
        return session != null
                && expectedSessionId != null
                && expectedRevision >= 0
                && session.sessionId().equals(expectedSessionId)
                && session.state() == StaffSessionState.ACTIVE
                && session.serverId().equalsIgnoreCase(serverId);
    }

    private void beginBackendHandoffClose(
            UUID playerId,
            UUID expectedSessionId,
            long expectedRevision,
            UUID transferId,
            CompletableFuture<Boolean> result
    ) {
        if (result.isCancelled()) {
            sourceHandoffs.abort(playerId, transferId);
            transitions.remove(playerId);
            return;
        }
        StaffSessionStore loaded = store.get();
        try {
            StaffSessionSnapshot current = loaded == null ? null : loaded.active(playerId).orElse(null);
            if (!validHandoffSource(current, expectedSessionId, expectedRevision)) {
                sourceHandoffs.finish(playerId, transferId);
                transitions.remove(playerId);
                result.complete(false);
                return;
            }
            restoreBackendHandoff(playerId, transferId, current, loaded, result);
        } catch (RuntimeException exception) {
            sourceHandoffs.finish(playerId, transferId);
            transitions.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "Staff backend handoff could not begin", exception);
            result.complete(false);
        }
    }

    private void restoreBackendHandoff(
            UUID playerId,
            UUID transferId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            CompletableFuture<Boolean> result
    ) {
        onEntity(playerId, player -> {
            try {
                if (!restoreSavedState(player, session)) {
                    markBackendHandoffRecovery(playerId, session, loaded, result, "Original location could not be restored");
                    return;
                }
                String checksum = codec.verifiedRestorationChecksum(
                        player, session.serverId(), session.snapshot(), session.checksum());
                if (result.isCancelled()) {
                    sourceHandoffs.abort(playerId, transferId);
                    markBackendHandoffRecovery(
                            playerId, session, loaded, result, "Staff handoff confirmation timed out");
                    return;
                }
                if (!submit(() -> completeBackendHandoff(
                        playerId, transferId, session, loaded, checksum, result))) {
                    sourceHandoffs.abort(playerId, transferId);
                    recoveryGate.retry(playerId);
                    removeRuntimeState(playerId);
                    result.complete(false);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Staff backend handoff restoration failed", exception);
                markBackendHandoffRecovery(playerId, session, loaded, result, "Runtime restoration failure");
            }
        }, () -> {
            recoveryGate.retry(playerId);
            result.complete(false);
        });
    }

    private void markBackendHandoffRecovery(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            CompletableFuture<Boolean> result,
            String reason
    ) {
        recoveryGate.retry(playerId);
        if (!submit(() -> {
            try {
                loaded.recoveryRequired(session.sessionId(), reason, clock.instant());
            } finally {
                result.complete(false);
            }
        })) {
            result.complete(false);
        }
    }

    private void completeBackendHandoff(
            UUID playerId,
            UUID transferId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            String restoredChecksum,
            CompletableFuture<Boolean> result
    ) {
        if (result.isCancelled()) {
            sourceHandoffs.abort(playerId, transferId);
            retainCancelledHandoffRecovery(playerId, session, loaded);
            return;
        }
        try {
            var committed = sourceHandoffs.commitIfActive(
                    playerId,
                    transferId,
                    () -> loaded.detach(
                            playerId,
                            session.sessionId(),
                            session.revision(),
                            session.serverId(),
                            restoredChecksum,
                            clock.instant()
                    ).isPresent()
            );
            if (committed.isEmpty()) {
                retainCancelledHandoffRecovery(playerId, session, loaded);
                return;
            }
            if (!committed.orElseThrow()) {
                recoveryGate.retry(playerId);
                removeRuntimeState(playerId);
                result.complete(false);
                return;
            }
            removeRuntimeState(playerId);
            recoveryGate.clear(playerId);
            handoffGaps.add(playerId);
            result.complete(true);
        } catch (RuntimeException exception) {
            sourceHandoffs.finish(playerId, transferId);
            recoveryGate.retry(playerId);
            removeRuntimeState(playerId);
            plugin.getLogger().log(Level.SEVERE, "Staff backend handoff detach failed", exception);
            result.complete(false);
        }
    }

    private void retainCancelledHandoffRecovery(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded
    ) {
        recoveryGate.retry(playerId);
        removeRuntimeState(playerId);
        try {
            loaded.recoveryRequired(
                    session.sessionId(),
                    "Staff handoff confirmation timed out or was aborted",
                    clock.instant()
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cancelled Staff handoff recovery persistence failed", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        StaffSessionSnapshot appliedSession = active.get(playerId);
        StaffSessionSnapshot session = appliedSession != null
                ? appliedSession
                : pendingLocalSessions.get(playerId);

        // A normal backend disconnect is not a Staff Mode exit. Restore this backend's
        // native state before Minecraft saves the player, then release only the local
        // snapshot lease. Network Staff Mode/vanish intent remains active.
        boolean pendingLocalLease = pendingLocalSessions.containsKey(playerId);
        if (session != null
                && session.state() == StaffSessionState.ACTIVE
                && serverId.equalsIgnoreCase(session.serverId())
                && (!transitions.contains(playerId)
                        || sourceHandoffs.active(playerId)
                        || pendingLocalLease)) {
            try {
                if (restoreSavedState(player, session)) {
                    String checksum = codec.verifiedRestorationChecksum(
                            player, session.serverId(), session.snapshot(), session.checksum());
                    if (!submit(() -> detachAfterQuit(playerId, session, checksum))) {
                        plugin.getLogger().warning(
                                "Staff Mode backend detach queue was full during disconnect for " + playerId);
                    }
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Staff Mode could not restore local state before disconnect; durable recovery remains available",
                        exception
                );
            }
        }

        removeRuntimeState(playerId);
        recoveryGate.clear(playerId);
        profileApplications.remove(playerId);
        snapshotRestorations.remove(playerId);
        pendingRankChecks.remove(playerId);
        handoffGaps.remove(playerId);
    }

    private void detachUnappliedLease(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            String capturedChecksum
    ) {
        if (!submit(() -> {
            try {
                loaded.detach(
                        playerId,
                        session.sessionId(),
                        session.revision(),
                        session.serverId(),
                        capturedChecksum,
                        clock.instant()
                );
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Staff Mode could not detach an unapplied backend lease for " + playerId,
                        exception
                );
            } finally {
                pendingLocalSessions.remove(playerId, session);
                transitions.remove(playerId);
            }
        })) {
            pendingLocalSessions.remove(playerId, session);
            transitions.remove(playerId);
        }
    }

    private void detachAfterQuit(UUID playerId, StaffSessionSnapshot session, String restoredChecksum) {
        StaffSessionStore loaded = store.get();
        if (loaded == null) {
            plugin.getLogger().warning("Staff session storage unavailable during disconnect detach for " + playerId);
            return;
        }
        try {
            if (loaded.detach(
                    playerId,
                    session.sessionId(),
                    session.revision(),
                    session.serverId(),
                    restoredChecksum,
                    clock.instant()
            ).isEmpty()) {
                plugin.getLogger().warning("Staff Mode disconnect detach lost its ownership fence for " + playerId);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Staff Mode disconnect detach failed for " + playerId, exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!protectedMode(playerId) || profileApplications.contains(playerId)) {
            return;
        }
        StaffRank rank = rankForAction(player);
        if (rank == null || !StaffModeAccessPolicy.allowsGameMode(rank, event.getNewGameMode())) {
            event.setCancelled(true);
            if (!transitions.contains(playerId)) {
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        "Your staff rank cannot use that game mode while staff mode is active."
                )));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(player);
        if (rank == StaffRank.DEVELOPER) {
            audit(player, rank, "damage-received",
                    event.getCause() + " damage=" + event.getFinalDamage());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player actor = null;
        if (event.getDamager() instanceof Player player) {
            actor = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            actor = player;
        }
        if (actor == null || !protectedMode(actor.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(actor);
        if (rank == StaffRank.DEVELOPER) {
            audit(actor, rank, "damage-dealt",
                    event.getEntityType() + " damage=" + event.getFinalDamage());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(player);
        StaffDutyTier tier = StaffDutyTier.of(rank);
        if (tier == null || tier == StaffDutyTier.HELPER) {
            event.setCancelled(true);
            return;
        }
        // Mod/Developer: logged-not-blocked. Admin/Founder: unrestricted but logged.
        audit(player, rank, "item-drop", describe(event.getItemDrop().getItemStack()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(player);
        StaffDutyTier tier = StaffDutyTier.of(rank);
        if (tier == null || tier == StaffDutyTier.HELPER) {
            event.setCancelled(true);
            return;
        }
        // Mod/Developer: logged-not-blocked. Admin/Founder: unrestricted but logged.
        audit(player, rank, "item-pickup", describe(event.getItem().getItemStack()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(player);
        StaffDutyTier tier = StaffDutyTier.of(rank);
        if (tier == null || tier == StaffDutyTier.HELPER) {
            event.setCancelled(true);
            return;
        }
        // Mod/Developer: logged-not-blocked (staff/empty inventory toggle). Admin/Founder: logged.
        audit(player, rank, "inventory-swap-hands",
                describe(event.getMainHandItem()) + " <-> " + describe(event.getOffHandItem()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(player);
        boolean ender = event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST;
        if (rank == null || StaffModeAccessPolicy.blocksInventoryMutation(rank, ender)) {
            event.setCancelled(true);
            return;
        }
        StaffDutyTier tier = StaffDutyTier.of(rank);
        if (tier == StaffDutyTier.MOD || tier == StaffDutyTier.DEVELOPER || tier == StaffDutyTier.ADMIN) {
            audit(player, rank, "inventory-edit",
                    event.getClick() + " container=" + event.getView().getTopInventory().getType()
                            + " item=" + describe(event.getCurrentItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = rankForAction(player);
        boolean ender = event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST;
        if (rank == null
                || StaffModeAccessPolicy.blocksInventoryMutation(rank, ender)
                || isStaffTool(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        StaffDutyTier tier = StaffDutyTier.of(rank);
        if (tier == StaffDutyTier.MOD || tier == StaffDutyTier.DEVELOPER || tier == StaffDutyTier.ADMIN) {
            audit(player, rank, "inventory-edit",
                    "drag container=" + event.getView().getTopInventory().getType()
                            + " cursor=" + describe(event.getOldCursor()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !protectedMode(player.getUniqueId())
                || event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }
        StaffRank rank = rankForAction(player);
        if (rank == null || StaffModeAccessPolicy.blocksEnderChestOpen(rank)) {
            event.setCancelled(true);
            if (rank != null) {
                player.sendMessage(StaffMessageStyle.style(Component.text(
                        "Ender chest access is unavailable at your staff rank while in staff mode."
                )));
            }
        }
    }

    StaffToolResolution resolveTool(Player player, ItemStack item, int heldSlot) {
        if (item == null || !item.hasItemMeta()) {
            return StaffToolResolution.untagged();
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        String id = data.get(staffToolKey, PersistentDataType.STRING);
        if (id == null) {
            return StaffToolResolution.untagged();
        }
        StaffToolDefinition tool = StaffToolDefinition.fromId(id).orElse(null);
        if (tool == null) {
            return StaffToolResolution.tagged(null, StaffToolSessionPolicy.Status.UNKNOWN_TOOL);
        }
        UUID playerId = player.getUniqueId();
        String activeToken = active.containsKey(playerId) && !transitions.contains(playerId)
                ? toolSessions.get(playerId)
                : null;
        StaffRank rank = activeToken == null ? null : rankForAction(player);
        StaffToolSessionPolicy.Status status = StaffToolSessionPolicy.validate(
                playerId,
                activeToken,
                heldSlot,
                tool,
                new StaffToolSessionPolicy.ItemContext(item.getType(), toolLayout.slot(tool)),
                data.get(staffToolOwnerKey, PersistentDataType.STRING),
                data.get(staffToolSessionKey, PersistentDataType.STRING),
                rank
        );
        return StaffToolResolution.tagged(tool, status);
    }

    boolean authorizedForTool(Player player, StaffToolDefinition tool) {
        UUID playerId = player.getUniqueId();
        if (!active.containsKey(playerId) || transitions.contains(playerId)) {
            return false;
        }
        StaffRank rank = rankForAction(player);
        return rank != null && !transitions.contains(playerId) && tool.availableFor(rank);
    }

    private StaffRank rankForAction(Player player) {
        UUID playerId = player.getUniqueId();
        if (transitions.contains(playerId)) {
            return null;
        }
        StaffRank cachedRank = ranks.get(playerId);
        StaffRank liveRank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        StaffModeRankReconciliationPolicy.Action action =
                StaffModeRankReconciliationPolicy.decide(cachedRank, liveRank);
        if (action == StaffModeRankReconciliationPolicy.Action.NONE) {
            return liveRank;
        }
        beginRankReconciliation(playerId, action);
        return null;
    }

    private void reconcileActiveRank(Player player) {
        UUID playerId = player.getUniqueId();
        if (!active(playerId) || transitions.contains(playerId)) {
            return;
        }
        StaffRank cachedRank = ranks.get(playerId);
        StaffRank liveRank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        StaffModeRankReconciliationPolicy.Action action =
                StaffModeRankReconciliationPolicy.decide(cachedRank, liveRank);
        if (action != StaffModeRankReconciliationPolicy.Action.NONE) {
            beginRankReconciliation(playerId, action);
        }
    }

    private void beginRankReconciliation(
            UUID playerId,
            StaffModeRankReconciliationPolicy.Action action
    ) {
        if (action == StaffModeRankReconciliationPolicy.Action.NONE
                || !active(playerId)
                || !transitions.add(playerId)) {
            return;
        }
        if (action == StaffModeRankReconciliationPolicy.Action.EXIT_SESSION) {
            message(playerId, RANK_REMOVED_MESSAGE);
            beginDurableExit(playerId, "Staff rank removal");
            return;
        }
        onEntity(playerId, this::applyLiveRankProfile);
    }

    private void applyLiveRankProfile(Player player) {
        UUID playerId = player.getUniqueId();
        if (!active(playerId)) {
            transitions.remove(playerId);
            return;
        }
        StaffRank cachedRank = ranks.get(playerId);
        StaffRank liveRank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        StaffModeRankReconciliationPolicy.Action action =
                StaffModeRankReconciliationPolicy.decide(cachedRank, liveRank);
        if (action == StaffModeRankReconciliationPolicy.Action.NONE) {
            transitions.remove(playerId);
            return;
        }
        if (action == StaffModeRankReconciliationPolicy.Action.EXIT_SESSION) {
            message(playerId, RANK_REMOVED_MESSAGE);
            beginDurableExit(playerId, "Staff rank removal");
            return;
        }
        try {
            applyStaffState(player, liveRank, true);
            ranks.put(playerId, liveRank);
            transitions.remove(playerId);
            player.sendMessage(StaffMessageStyle.style(Component.text("Your active staff-mode profile was updated for your current rank.")));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Staff rank profile reconciliation failed", exception);
            message(playerId, "Your staff rank changed, but the new profile could not be applied; restoring your saved state.");
            beginDurableExit(playerId, "Staff rank profile reconciliation failure");
        }
    }

    private void beginDurableExit(UUID playerId, String operation) {
        if (!submit(() -> {
            StaffSessionStore loaded = store.get();
            if (loaded == null) {
                transitions.remove(playerId);
                message(playerId, "Staff session storage is unavailable; exit failed safely.");
                return;
            }
            StaffSessionSnapshot session;
            try {
                session = loaded.beginExit(playerId, clock.instant()).orElse(null);
            } catch (RuntimeException exception) {
                transitions.remove(playerId);
                plugin.getLogger().log(Level.SEVERE, operation + " transition failed", exception);
                message(playerId, "Staff mode exit could not begin safely.");
                return;
            }
            if (session == null) {
                transitions.remove(playerId);
                message(playerId, "No active staff session was found.");
                return;
            }
            restoreAndVerify(playerId, session, loaded);
        })) {
            transitions.remove(playerId);
            message(playerId, "The bounded work queue is full; staff mode exit did not start.");
        }
    }

    private void restoreAndVerify(UUID playerId, StaffSessionSnapshot session, StaffSessionStore loaded) {
        onEntity(playerId, player -> {
            try {
                if (!restoreSavedState(player, session)) {
                    recoveryGate.retry(playerId);
                    submit(() -> loaded.recoveryRequired(
                            session.sessionId(), "Original location could not be restored", clock.instant()
                    ));
                    player.sendMessage(StaffMessageStyle.style(Component.text("Restoration could not complete; recovery remains pending.")));
                    return;
                }
                String restoredChecksum = codec.verifiedRestorationChecksum(
                        player, session.serverId(), session.snapshot(), session.checksum());
                if (!submit(() -> completeRestoration(playerId, session, loaded, restoredChecksum))) {
                    retainRecoveryAfterRuntimeExit(playerId);
                    player.sendMessage(StaffMessageStyle.style(Component.text(
                            "State was restored, but durable verification is still pending; contact an administrator."
                    )));
                }
            } catch (RuntimeException exception) {
                recoveryGate.retry(playerId);
                submit(() -> loaded.recoveryRequired(
                        session.sessionId(), "Runtime restoration failure", clock.instant()
                ));
                plugin.getLogger().log(Level.SEVERE, "Staff state restoration failed", exception);
                player.sendMessage(StaffMessageStyle.style(Component.text("Restoration failed safely; your original snapshot remains durable.")));
            }
        });
    }

    private boolean restoreSavedState(Player player, StaffSessionSnapshot session) {
        if (!serverId.equals(session.serverId())) {
            throw new IllegalStateException(
                    "refusing to restore staff snapshot owned by backend " + session.serverId()
            );
        }
        if (!codec.checksum(session.snapshot()).equals(session.checksum())) {
            throw new IllegalStateException("saved staff snapshot integrity check failed");
        }
        UUID playerId = player.getUniqueId();
        profileApplications.add(playerId);
        snapshotRestorations.add(playerId);
        try {
            removeStaffTools(player);
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            return codec.restore(player, session.snapshot());
        } finally {
            snapshotRestorations.remove(playerId);
            profileApplications.remove(playerId);
        }
    }

    private void completeRestoration(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            String restoredChecksum
    ) {
        boolean closed;
        try {
            closed = loaded.completeExit(session.sessionId(), restoredChecksum, clock.instant());
        } catch (RuntimeException exception) {
            retainRecoveryAfterRuntimeExit(playerId);
            plugin.getLogger().log(Level.SEVERE, "Staff session closure verification failed", exception);
            safeMessage(playerId, "State was restored, but durable closure verification failed; contact an administrator.");
            return;
        }
        if (!closed) {
            retainRecoveryAfterRuntimeExit(playerId);
            safeMessage(playerId, "State was restored, but checksum verification requires administrator review.");
            return;
        }
        completeRuntimeExit(playerId);
        safeMessage(playerId, "Staff mode exited; your exact saved state was restored and verified.");
    }

    private void retainRecoveryAfterRuntimeExit(UUID playerId) {
        recoveryGate.retry(playerId);
        removeRuntimeState(playerId);
        handoffGaps.remove(playerId);
        try {
            exitListener.accept(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Post-exit staff-mode cleanup callback failed", exception);
        }
    }

    private void completeRuntimeExit(UUID playerId) {
        removeRuntimeState(playerId);
        recoveryGate.clear(playerId);
        handoffGaps.remove(playerId);
        try {
            exitListener.accept(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Post-exit staff-mode cleanup callback failed", exception);
        }
    }

    private void failEntry(UUID playerId) {
        pendingLocalSessions.remove(playerId);
        transitions.remove(playerId);
        abandonHandoffGap(playerId);
    }

    private void abandonHandoffGap(UUID playerId) {
        if (!handoffGaps.remove(playerId)) {
            return;
        }
        try {
            exitListener.accept(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Post-handoff staff-mode cleanup callback failed", exception);
        }
    }

    private void removeRuntimeState(UUID playerId) {
        active.remove(playerId);
        pendingLocalSessions.remove(playerId);
        ranks.remove(playerId);
        toolSessions.remove(playerId);
    }

    private void applyStaffState(Player player, StaffRank rank, boolean preserveAllowedGameMode) {
        UUID playerId = player.getUniqueId();
        GameMode targetGameMode = preserveAllowedGameMode
                ? StaffModeAccessPolicy.reconciledGameMode(rank, player.getGameMode())
                : StaffModeAccessPolicy.initialGameMode(rank);
        profileApplications.add(playerId);
        String toolSession = UUID.randomUUID().toString();
        toolSessions.put(playerId, toolSession);
        try {
            player.closeInventory();
            player.getInventory().clear();
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.setLevel(0);
            player.setExp(0);
            player.setTotalExperience(0);
            player.setFoodLevel(20);
            player.setSaturation(20);
            player.setExhaustion(0);
            org.bukkit.attribute.AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maximumHealth == null) {
                throw new IllegalStateException("player maximum-health attribute is unavailable");
            }
            player.setHealth(maximumHealth.getValue());
            player.setFireTicks(0);
            player.setFallDistance(0);
            player.setInvulnerable(true);
            player.setCollidable(false);
            player.setCanPickupItems(false);
            gameModeTransitionGuardBegin.accept(playerId);
            try {
                player.setGameMode(targetGameMode);
            } finally {
                gameModeTransitionGuardEnd.accept(playerId);
            }
            if (player.getGameMode() != targetGameMode) {
                throw new IllegalStateException("staff game mode transition was rejected");
            }
            player.setAllowFlight(true);
            player.setFlying(true);
            for (StaffToolDefinition tool : StaffToolDefinition.values()) {
                if (tool.availableFor(rank)) {
                    player.getInventory().setItem(toolLayout.slot(tool), item(playerId, toolSession, tool));
                }
            }
            player.updateInventory();
        } finally {
            profileApplications.remove(playerId);
        }
    }

    private boolean protectedMode(UUID playerId) {
        return active.containsKey(playerId) || transitions.contains(playerId) || handoffGaps.contains(playerId);
    }

    private ItemStack item(UUID playerId, String toolSession, StaffToolDefinition tool) {
        ItemStack item = ItemStack.of(tool.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tool.displayName()));
        meta.lore(List.of(Component.text(tool.description(), NamedTextColor.GRAY)));
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(staffToolKey, PersistentDataType.STRING, tool.id());
        data.set(staffToolOwnerKey, PersistentDataType.STRING, playerId.toString());
        data.set(staffToolSessionKey, PersistentDataType.STRING, toolSession);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isStaffTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(staffToolKey, PersistentDataType.STRING);
    }

    private void removeStaffTools(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            if (isStaffTool(contents[index])) {
                player.getInventory().setItem(index, null);
            }
        }
    }

    public static StaffRank rank(Player player) {
        return PaperStaffRankResolver.resolve(player::hasPermission).orElseThrow(() ->
                new IllegalStateException("An explicit EnthusiaStaff rank is required"));
    }

    private boolean submit(Runnable operation) {
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Staff session operation skipped because the bounded queue is full");
            return false;
        }
    }

    private void message(UUID playerId, String message) {
        onEntity(playerId, player -> player.sendMessage(StaffMessageStyle.style(Component.text(message))));
    }

    private void safeMessage(UUID playerId, String message) {
        try {
            message(playerId, message);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Staff-mode player notification failed", exception);
        }
    }

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        onEntity(playerId, operation, () -> {
        });
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                retired.run();
                return;
            }
            boolean scheduled = player.getScheduler().execute(
                    plugin,
                    () -> operation.accept(player),
                    retired,
                    1L
            );
            if (!scheduled) {
                retired.run();
            }
        });
    }
}
