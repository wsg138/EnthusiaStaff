package net.enthusia.staff.paper.freeze;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeManager implements Listener {
    private static final Duration OFFLINE_EXPIRATION = Duration.ofMinutes(10);

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<FreezeStore> store;
    private final ExecutorService workers;
    private final FreezeRuntimeState runtimeState = new FreezeRuntimeState();

    public FreezeManager(
            JavaPlugin plugin,
            Clock clock,
            Supplier<FreezeStore> store,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.store = store;
        this.workers = workers;
    }

    public boolean isRestricted(UUID playerId) {
        return runtimeState.isRestricted(playerId);
    }

    public void applyOnline(UUID playerId) {
        long generation = runtimeState.apply(playerId);
        onEntity(playerId, player -> {
            if (!runtimeState.isCurrentFrozen(playerId, generation)) {
                return;
            }
            securePlayer(player);
        }, () -> runtimeState.retireIfCurrent(playerId, generation));
    }

    public void releaseOnline(UUID playerId) {
        long generation = runtimeState.release(playerId);
        onEntity(playerId, player -> {
            if (!runtimeState.isCurrentRelease(playerId, generation)) {
                return;
            }
            player.sendMessage(Component.text("Your staff freeze has been released."));
        }, () -> runtimeState.retireIfCurrent(playerId, generation));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        verify(player.getUniqueId(), player.getName());
    }

    public void verify(Player player) {
        verify(player.getUniqueId(), player.getName());
    }

    public void verify(UUID playerId, String playerName) {
        String displayName = playerName == null || playerName.isBlank()
                ? playerId.toString()
                : playerName;
        long verificationToken = runtimeState.beginVerification(playerId);
        if (submit(() -> verifyStoredState(playerId, displayName, verificationToken))) {
            return;
        }
        plugin.getLogger().severe("Freeze verification was not scheduled for " + displayName
                + "; the player remains restricted until staff intervene");
        alertStaffDuringVerification(playerId, verificationToken,
                "Freeze verification could not run for " + displayName
                        + ". The player remains restricted. Use /unfreeze after review.");
    }

    private void verifyStoredState(UUID playerId, String displayName, long verificationToken) {
        try {
            FreezeStore loaded = store.get();
            if (loaded == null) {
                if (runtimeState.isVerificationCurrent(playerId, verificationToken)) {
                    plugin.getLogger().severe(
                            "Freeze storage is unavailable; the joining player remains restricted"
                    );
                }
                return;
            }
            boolean active = loaded.active(playerId, clock.instant()).isPresent();
            if (!runtimeState.resolveVerification(playerId, verificationToken, active) || !active) {
                return;
            }
            onEntity(playerId, player -> {
                if (!runtimeState.isCurrentFrozen(playerId, verificationToken)) {
                    return;
                }
                securePlayer(player);
            });
            alertStaff(playerId, verificationToken, "Freeze restored for " + displayName
                    + ". Use /unfreeze or /freeze keep after review.");
        } catch (RuntimeException exception) {
            if (runtimeState.isVerificationCurrent(playerId, verificationToken)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Freeze recovery lookup failed; the joining player remains restricted", exception);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!runtimeState.retire(playerId)) {
            return;
        }
        Instant now = clock.instant();
        submit(() -> {
            FreezeStore loaded = store.get();
            if (loaded != null) {
                loaded.disconnected(playerId, now.plus(OFFLINE_EXPIRATION), now);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!restricted(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }
        Location stationary = from.clone();
        stationary.setYaw(to.getYaw());
        stationary.setPitch(to.getPitch());
        event.setTo(stationary);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && restricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        if (!restricted(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        relayFrozenChat(event.getPlayer(), event.message());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!restricted(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        relayFrozenChat(event.getPlayer(), Component.text(event.getMessage()));
    }

    void securePlayer(Player player) {
        player.leaveVehicle();
        player.closeInventory();
        player.sendMessage(Component.text("You have been frozen by network staff."));
    }

    private boolean restricted(Player player) {
        return runtimeState.isRestricted(player.getUniqueId());
    }

    private void cancel(Player player, Cancellable event) {
        if (restricted(player)) {
            event.setCancelled(true);
        }
    }

    private void relayFrozenChat(Player player, Component body) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Component rendered = Component.text("<" + playerName + "> ").append(body);
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player current = plugin.getServer().getPlayer(playerId);
            if (current != null) {
                current.sendMessage(rendered);
            }
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(staff -> !staff.getUniqueId().equals(playerId))
                    .filter(staff -> staff.hasPermission("enthusiastaff.freeze.chat"))
                    .forEach(staff -> staff.sendMessage(Component.text("[Frozen Chat] ").append(rendered)));
        });
    }

    private void alertStaff(UUID playerId, long generation, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (!runtimeState.isCurrentFrozen(playerId, generation)) {
                return;
            }
            sendAlert(message);
        });
    }

    private void alertStaffDuringVerification(UUID playerId, long generation, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (!runtimeState.isVerificationCurrent(playerId, generation)) {
                return;
            }
            sendAlert(message);
        });
    }

    private void sendAlert(String message) {
        plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("enthusiastaff.freeze"))
                .forEach(player -> player.sendMessage(Component.text(message)));
    }

    private boolean submit(Runnable operation) {
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().warning("Freeze persistence operation skipped because the bounded queue is full");
            return false;
        }
    }

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        onEntity(playerId, operation, () -> {
        });
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable unavailable) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                unavailable.run();
                return;
            }
            player.getScheduler().execute(plugin, () -> operation.accept(player), null, 1L);
        });
    }
}
