package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.kyori.adventure.text.Component;
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
    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final Supplier<StaffSessionStore> store;
    private final ExecutorService workers;
    private final StaffStateCodec codec = new StaffStateCodec();
    private final CombatStatusAdapter combat;
    private final NamespacedKey staffToolKey;
    private final NamespacedKey staffToolOwnerKey;
    private final NamespacedKey staffToolSessionKey;
    private final Map<UUID, StaffSessionSnapshot> active = new ConcurrentHashMap<>();
    private final Map<UUID, StaffRank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, String> toolSessions = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> transitions = ConcurrentHashMap.newKeySet();
    private final StaffModeRecoveryGate recoveryGate = new StaffModeRecoveryGate(transitions);
    private final java.util.Set<UUID> profileApplications = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> pendingRankChecks = ConcurrentHashMap.newKeySet();
    private final StaffModeActivationCoordinator activation;
    private final AtomicBoolean rankReconciliationStarted = new AtomicBoolean();
    private volatile Consumer<UUID> exitListener = ignored -> {
    };

    public StaffModeManager(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<StaffSessionStore> store,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.serverId = serverId;
        this.store = store;
        this.workers = workers;
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
        return active.containsKey(playerId);
    }

    public CombatStatusAdapter combat() {
        return combat;
    }

    public void setExitListener(Consumer<UUID> exitListener) {
        this.exitListener = java.util.Objects.requireNonNull(exitListener);
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
            player.sendMessage(Component.text("A staff-mode transition is already in progress."));
            return;
        }
        CombatStatusAdapter.Status combatStatus = combat.status(player);
        if (combatStatus != CombatStatusAdapter.Status.CLEAR) {
            transitions.remove(playerId);
            player.sendMessage(Component.text(combatStatus == CombatStatusAdapter.Status.TAGGED
                    ? "You cannot enter staff mode while combat tagged."
                    : "Combat state could not be verified; staff mode entry failed safely."));
            return;
        }
        StaffStateCodec.Captured captured;
        try {
            captured = codec.capture(player, serverId);
        } catch (RuntimeException exception) {
            transitions.remove(playerId);
            plugin.getLogger().log(Level.SEVERE, "Staff state snapshot capture failed", exception);
            player.sendMessage(Component.text("Your state could not be snapshotted; staff mode was not entered."));
            return;
        }
        if (!submit(() -> {
            StaffSessionStore loaded = store.get();
            if (loaded == null) {
                transitions.remove(playerId);
                message(playerId, "Staff session storage is not ready; your inventory was not changed.");
                return;
            }
            try {
                StaffSessionSnapshot session = loaded.begin(
                        playerId, serverId, captured.schemaVersion(), captured.checksum(),
                        captured.snapshot(), clock.instant()
                );
                onEntity(playerId, current -> activateDurableSession(
                        playerId,
                        session,
                        loaded,
                        current,
                        rank,
                        StaffModeActivationCoordinator.ActivationPath.INITIAL_ENTRY,
                        "Staff mode entered after durable snapshot commit."
                ));
            } catch (RuntimeException exception) {
                transitions.remove(playerId);
                plugin.getLogger().log(Level.SEVERE, "Staff session entry failed", exception);
                message(playerId, "Staff mode entry failed before your inventory was changed.");
            }
        })) {
            transitions.remove(playerId);
            player.sendMessage(Component.text("The bounded work queue is full; staff mode was not entered."));
        }
    }

    public void exit(Player player) {
        UUID playerId = player.getUniqueId();
        if (!transitions.add(playerId)) {
            player.sendMessage(Component.text("A staff-mode transition is already in progress."));
            return;
        }
        beginDurableExit(playerId, "Staff mode exit");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        recover(event.getPlayer());
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
                    return;
                }
                if (!session.serverId().equals(serverId)) {
                    loaded.recoveryRequired(
                            session.sessionId(),
                            "Original backend is required for restoration",
                            clock.instant()
                    );
                    message(playerId, "Your staff session requires recovery on backend " + session.serverId() + '.');
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
                    message(playerId, "Your explicit staff rank is no longer assigned; restoring your saved state.");
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

    private void finishActiveRecovery(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            Player player
    ) {
        StaffRank currentRank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (StaffModeRankReconciliationPolicy.decide(null, currentRank)
                == StaffModeRankReconciliationPolicy.Action.EXIT_SESSION) {
            player.sendMessage(Component.text(
                    "Your explicit staff rank is no longer assigned; restoring your saved state."
            ));
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
                player.sendMessage(Component.text(
                        "The bounded work queue is full; staff session recovery did not continue."
                ));
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
                () -> applyStaffState(player, rank),
                () -> {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElseThrow(() ->
                            new IllegalStateException("staff session disappeared during activation rollback"));
                    restoreAndVerify(playerId, exiting, loaded);
                },
                message -> player.sendMessage(Component.text(message)),
                successMessage
        );
        if (!activated) {
            toolSessions.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        active.remove(playerId);
        ranks.remove(playerId);
        toolSessions.remove(playerId);
        recoveryGate.clear(playerId);
        profileApplications.remove(playerId);
        pendingRankChecks.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!protectedMode(playerId) || profileApplications.contains(playerId)) {
            return;
        }
        StaffRank rank = rankForAction(player);
        if (rank == null || event.getNewGameMode() != StaffModeAccessPolicy.requiredGameMode(rank)) {
            event.setCancelled(true);
            if (!transitions.contains(playerId)) {
                player.sendMessage(Component.text(
                        "Your staff rank cannot use that game mode while staff mode is active."
                ));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && protectedMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && protectedMode(player.getUniqueId())) {
            event.setCancelled(true);
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player && protectedMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (protectedMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && protectedMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (protectedMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
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
                player.sendMessage(Component.text(
                        "Ender chest access is unavailable at your staff rank while in staff mode."
                ));
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
                item.getType(),
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
            message(playerId, "Your explicit staff rank is no longer assigned; restoring your saved state.");
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
            message(playerId, "Your explicit staff rank is no longer assigned; restoring your saved state.");
            beginDurableExit(playerId, "Staff rank removal");
            return;
        }
        try {
            applyStaffState(player, liveRank);
            ranks.put(playerId, liveRank);
            transitions.remove(playerId);
            player.sendMessage(Component.text("Your active staff-mode profile was updated for your current rank."));
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
                removeStaffTools(player);
                if (!codec.restore(player, session.snapshot())) {
                    submit(() -> loaded.recoveryRequired(
                            session.sessionId(), "Original location could not be restored", clock.instant()
                    ));
                    player.sendMessage(Component.text("Restoration could not complete; recovery remains pending."));
                    return;
                }
                StaffStateCodec.Captured restored = codec.capture(player, session.serverId());
                if (!submit(() -> completeRestoration(playerId, session, loaded, restored))) {
                    player.sendMessage(Component.text(
                            "State was restored, but durable verification is still pending; remain disconnected or contact staff."
                    ));
                }
            } catch (RuntimeException exception) {
                submit(() -> loaded.recoveryRequired(
                        session.sessionId(), "Runtime restoration failure", clock.instant()
                ));
                plugin.getLogger().log(Level.SEVERE, "Staff state restoration failed", exception);
                player.sendMessage(Component.text("Restoration failed safely; your original snapshot remains durable."));
            }
        });
    }

    private void completeRestoration(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            StaffStateCodec.Captured restored
    ) {
        boolean closed;
        try {
            closed = loaded.completeExit(session.sessionId(), restored.checksum(), clock.instant());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Staff session closure verification failed", exception);
            safeMessage(playerId, "State was restored, but durable closure verification failed; contact an administrator.");
            return;
        }
        if (!closed) {
            safeMessage(playerId, "State was restored, but checksum verification requires administrator review.");
            return;
        }
        active.remove(playerId);
        ranks.remove(playerId);
        toolSessions.remove(playerId);
        recoveryGate.clear(playerId);
        try {
            exitListener.accept(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Post-exit staff-mode cleanup callback failed", exception);
        }
        safeMessage(playerId, "Staff mode exited; your exact saved state was restored and verified.");
    }

    private void applyStaffState(Player player, StaffRank rank) {
        UUID playerId = player.getUniqueId();
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
            player.setGameMode(StaffModeAccessPolicy.requiredGameMode(rank));
            if (player.getGameMode() != StaffModeAccessPolicy.requiredGameMode(rank)) {
                throw new IllegalStateException("required staff game mode was rejected");
            }
            player.setAllowFlight(true);
            player.setFlying(true);
            for (StaffToolDefinition tool : StaffToolDefinition.values()) {
                if (tool.availableFor(rank)) {
                    player.getInventory().setItem(tool.slot(), item(playerId, toolSession, tool));
                }
            }
            player.updateInventory();
        } finally {
            profileApplications.remove(playerId);
        }
    }

    private boolean protectedMode(UUID playerId) {
        return active.containsKey(playerId) || transitions.contains(playerId);
    }

    private ItemStack item(UUID playerId, String toolSession, StaffToolDefinition tool) {
        ItemStack item = ItemStack.of(tool.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tool.displayName()));
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
        onEntity(playerId, player -> player.sendMessage(Component.text(message)));
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
