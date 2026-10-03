package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

/**
 * Tiered world-interaction enforcement for on-duty staff (overnight permission model).
 *
 * <ul>
 *   <li>HELPER — full lockdown, as before.</li>
 *   <li>MOD — logged-not-blocked for block placement/breaking and block/container interaction;
 *   other world uses stay blocked.</li>
 *   <li>ADMIN — unrestricted, but every interaction is audit-logged.</li>
 * </ul>
 *
 * <p>An unresolvable tier while staff mode is active fails closed (block). Air clicks remain
 * available for every tier so dedicated staff tools keep their normal interaction path without
 * enabling block, entity, resource or consumption actions.
 */
public final class StaffModeWorldInteractionListener implements Listener {
    private final StaffModeManager staffMode;

    public StaffModeWorldInteractionListener(StaffModeManager staffMode) {
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksBlockEdit(tier)) {
            event.setCancelled(true);
        } else if (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)) {
            staffMode.logStaffAction(player, "block-break", describe(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksBlockEdit(tier)) {
            event.setCancelled(true);
        } else if (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)) {
            staffMode.logStaffAction(player, "block-place", describe(event.getBlockPlaced()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        cancelWorldUse(event.getPlayer(), event, "bucket-fill", describe(event.getBlockClicked()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        cancelWorldUse(event.getPlayer(), event, "bucket-empty", describe(event.getBlockClicked()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        cancelWorldUse(event.getPlayer(), event, "block-harvest", describe(event.getHarvestedBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        Action action = event.getAction();
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksBlockInteraction(tier, action)) {
            event.setCancelled(true);
            return;
        }
        if (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)
                && action != Action.LEFT_CLICK_AIR
                && action != Action.RIGHT_CLICK_AIR) {
            staffMode.logStaffAction(player, "block-interact",
                    action + " " + describe(event.getClickedBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelWorldUse(event.getPlayer(), event, "entity-interact",
                event.getRightClicked().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        cancelWorldUse(event.getPlayer(), event, "entity-interact-at",
                event.getRightClicked().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        cancelWorldUse(event.getPlayer(), event, "armor-stand-manipulate",
                event.getRightClicked().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        cancelWorldUse(event.getPlayer(), event, "entity-shear",
                event.getEntity().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        cancelWorldUse(event.getPlayer(), event, "item-consume",
                event.getItem().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        cancelWorldUse(event.getPlayer(), event, "fishing", event.getState().toString());
    }

    private boolean onDuty(Player player) {
        return staffMode.active(player.getUniqueId());
    }

    private void cancelWorldUse(Player player, Cancellable event, String action, String detail) {
        if (!onDuty(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksWorldUse(tier)) {
            event.setCancelled(true);
        } else if (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)) {
            staffMode.logStaffAction(player, action, detail);
        }
    }

    private static String describe(Block block) {
        if (block == null) {
            return "unknown";
        }
        org.bukkit.Location location = block.getLocation();
        String world = location.getWorld() == null ? "?" : location.getWorld().getName();
        return block.getType() + " " + world + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
