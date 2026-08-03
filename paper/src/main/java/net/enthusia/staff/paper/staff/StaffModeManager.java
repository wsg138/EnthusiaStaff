package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
    private final Map<UUID, StaffSessionSnapshot> active = new ConcurrentHashMap<>();
    private final Map<UUID, StaffRank> ranks = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> transitions = ConcurrentHashMap.newKeySet();
    private final StaffModeActivationCoordinator activation;
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
                plugin.getLogger().log(Level.SEVERE, "Staff session exit transition failed", exception);
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
            player.sendMessage(Component.text("The bounded work queue is full; staff mode exit did not start."));
        }
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
        submit(() -> {
            StaffSessionStore loaded = store.get();
            if (loaded == null) {
                return;
            }
            try {
                StaffSessionSnapshot session = loaded.active(playerId).orElse(null);
                if (session == null) {
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
                    restoreAndVerify(playerId, session, loaded);
                    return;
                }
                if (rankSnapshot == null) {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElse(session);
                    message(playerId, "Your explicit staff rank is no longer assigned; restoring your saved state.");
                    restoreAndVerify(playerId, exiting, loaded);
                    return;
                }
                onEntity(playerId, current -> finishActiveRecovery(playerId, session, loaded, current));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Staff session recovery failed", exception);
                message(playerId, "Your staff session could not be recovered automatically; contact an administrator.");
            }
        });
    }

    private void finishActiveRecovery(
            UUID playerId,
            StaffSessionSnapshot session,
            StaffSessionStore loaded,
            Player player
    ) {
        StaffRank currentRank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (currentRank == null) {
            player.sendMessage(Component.text(
                    "Your explicit staff rank is no longer assigned; restoring your saved state."
            ));
            submit(() -> {
                try {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElse(session);
                    restoreAndVerify(playerId, exiting, loaded);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Staff session recovery exit failed", exception);
                    message(playerId, "Your staff session could not be recovered automatically; contact an administrator.");
                }
            });
            return;
        }
        if (!transitions.add(playerId)) {
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
        activation.activate(
                playerId,
                session,
                loaded,
                rank,
                path,
                () -> applyStaffState(player, rank),
                () -> {
                    StaffSessionSnapshot exiting = loaded.beginExit(playerId, clock.instant()).orElse(session);
                    restoreAndVerify(playerId, exiting, loaded);
                },
                message -> player.sendMessage(Component.text(message)),
                successMessage
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        active.remove(playerId);
        ranks.remove(playerId);
        transitions.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!active(playerId) || transitions.contains(playerId)) {
            return;
        }
        StaffRank rank = ranks.get(playerId);
        if (rank == null || event.getNewGameMode() != StaffModeAccessPolicy.requiredGameMode(rank)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your staff rank cannot use that game mode while staff mode is active."));
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
        StaffRank rank = currentRank(player);
        if (transitions.contains(player.getUniqueId())
                || StaffModeAccessPolicy.blocksAllInventoryMutation(rank)) {
            event.setCancelled(true);
            return;
        }
        boolean ender = event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST;
        if ((ender && StaffModeAccessPolicy.blocksEnderChestMutation(rank))
                || isStaffTool(event.getCurrentItem()) || isStaffTool(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !protectedMode(player.getUniqueId())) {
            return;
        }
        StaffRank rank = currentRank(player);
        boolean ender = event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST;
        if (StaffModeAccessPolicy.blocksAllInventoryMutation(rank)
                || isStaffTool(event.getOldCursor())
                || (ender && StaffModeAccessPolicy.blocksEnderChestMutation(rank))
                || transitions.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !active(player.getUniqueId())
                || event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }
        StaffRank rank = currentRank(player);
        if (StaffModeAccessPolicy.blocksEnderChestOpen(rank)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Ender chest access is unavailable at your staff rank while in staff mode."));
        }
    }

    private StaffRank currentRank(Player player) {
        StaffRank cached = ranks.get(player.getUniqueId());
        return cached == null ? rank(player) : cached;
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
                    transitions.remove(playerId);
                    return;
                }
                StaffStateCodec.Captured restored = codec.capture(player, session.serverId());
                submit(() -> {
                    boolean closed = loaded.completeExit(session.sessionId(), restored.checksum(), clock.instant());
                    if (closed) {
                        active.remove(playerId);
                        ranks.remove(playerId);
                        exitListener.accept(playerId);
                        message(playerId, "Staff mode exited; your exact saved state was restored and verified.");
                    } else {
                        message(playerId, "State was restored, but checksum verification requires administrator review.");
                    }
                    transitions.remove(playerId);
                });
            } catch (RuntimeException exception) {
                submit(() -> loaded.recoveryRequired(
                        session.sessionId(), "Runtime restoration failure", clock.instant()
                ));
                transitions.remove(playerId);
                plugin.getLogger().log(Level.SEVERE, "Staff state restoration failed", exception);
                player.sendMessage(Component.text("Restoration failed safely; your original snapshot remains durable."));
            }
        });
    }

    private void applyStaffState(Player player, StaffRank rank) {
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
        player.setAllowFlight(true);
        player.setFlying(true);
        List<Tool> tools = new ArrayList<>(List.of(
                new Tool(Material.COMPASS, "random-teleport", "Random Player Teleport"),
                new Tool(Material.PLAYER_HEAD, "player-inspector", "Player Inspector"),
                new Tool(Material.PACKED_ICE, "freeze", "Freeze"),
                new Tool(Material.BOOK, "reports", "Reports"),
                new Tool(Material.SPYGLASS, "spectate", "Follow or Spectate"),
                new Tool(Material.ENDER_EYE, "vanish", "Vanish"),
                new Tool(Material.ECHO_SHARD, "staff-chat", "Staff Chat")
        ));
        if (StaffModeAccessPolicy.hasAdvancedStaffTools(rank)) {
            tools.add(new Tool(Material.BLAZE_ROD, "cheat-tester", "Cheat Tester"));
            tools.add(new Tool(Material.NETHER_STAR, "staff-tools", "Staff Tools Menu"));
        }
        for (int slot = 0; slot < tools.size(); slot++) {
            player.getInventory().setItem(slot, item(tools.get(slot)));
        }
        player.updateInventory();
    }

    private boolean protectedMode(UUID playerId) {
        return active.containsKey(playerId) || transitions.contains(playerId);
    }

    private ItemStack item(Tool tool) {
        ItemStack item = ItemStack.of(tool.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tool.displayName()));
        meta.getPersistentDataContainer().set(staffToolKey, PersistentDataType.STRING, tool.id());
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

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.getScheduler().execute(plugin, () -> operation.accept(player), null, 1L);
            }
        });
    }

    private record Tool(Material material, String id, String displayName) {
    }
}
