package net.enthusia.staff.paper.inventory;

import java.util.Objects;
import net.enthusia.staff.paper.api.InventoryLockService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerPickItemEvent;
import org.bukkit.event.player.PlayerSwapWithEquipmentSlotEvent;

/**
 * Closes mutation paths that are not inventory-click based while an inventory
 * patch owns the player's state. The coordinator handles ordinary inventory,
 * pickup/drop, held-slot and interaction events; this guard covers durability,
 * consumption, death/totem and direct equipment/pick-item paths so queued login
 * recovery cannot race a vanilla state mutation before final verification.
 */
public final class InventoryRecoveryGuard implements Listener {
    private final InventoryLockService locks;

    public InventoryRecoveryGuard(InventoryLockService locks) {
        this.locks = Objects.requireNonNull(locks, "locks");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && locked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && locked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickItem(PlayerPickItemEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapEquipment(PlayerSwapWithEquipmentSlotEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean locked(Player player) {
        return locks.isLocked(player.getUniqueId());
    }
}
