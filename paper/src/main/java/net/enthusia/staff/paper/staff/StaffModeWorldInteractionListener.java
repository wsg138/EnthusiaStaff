package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Tiered world-interaction enforcement for on-duty staff.
 *
 * <p>Helpers are observational: they may silently open world containers to inspect them, but
 * every inventory mutation path is cancelled while that container is open. Mod may edit
 * containers and ordinary blocks with audit logging. Developer/Admin/Founder world interactions
 * are unrestricted but audited; Developer remains separate from moderation authority.</p>
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
        if (staffMode.isUnrestricted(player)) {
            staffMode.logStaffAction(player, "block-break", describe(event.getBlock()));
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
        if (staffMode.isUnrestricted(player)) {
            staffMode.logStaffAction(player, "block-place", describe(event.getBlockPlaced()));
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
        if (staffMode.isUnrestricted(player)) {
            if (action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR) {
                staffMode.logStaffAction(player, "block-interact",
                        action + " " + describe(event.getClickedBlock()));
            }
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (action == Action.RIGHT_CLICK_BLOCK
                && isContainer(event.getClickedBlock())
                && StaffModeWorldInteractionPolicy.allowsContainerView(tier)) {
            staffMode.logStaffAction(player, "container-view", describe(event.getClickedBlock()));
            return;
        }
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
    public void onContainerEdit(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !onDuty(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isProtectedContainerInventory(top)) {
            return;
        }
        if (staffMode.isUnrestricted(player)) {
            staffMode.logStaffAction(player, "container-edit",
                    top.getType() + " raw-slot=" + event.getRawSlot() + " action=" + event.getAction());
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEdit(tier)) {
            // Cancel every click while a protected container is open. Cancelling only clicks in
            // the top inventory is insufficient: shift-click, hotbar swap, double-click/collect,
            // and similar actions can mutate the container from the player's inventory.
            event.setCancelled(true);
            return;
        }
        if (StaffModeWorldInteractionPolicy.logsContainerEdit(tier)) {
            staffMode.logStaffAction(player, "container-edit",
                    top.getType() + " raw-slot=" + event.getRawSlot() + " action=" + event.getAction());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !onDuty(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isProtectedContainerInventory(top)) {
            return;
        }
        if (staffMode.isUnrestricted(player)) {
            boolean touchesContainer = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
            if (touchesContainer) {
                staffMode.logStaffAction(player, "container-edit", top.getType() + " drag");
            }
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEdit(tier)) {
            event.setCancelled(true);
            return;
        }
        boolean touchesProtectedContainer = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (touchesProtectedContainer && StaffModeWorldInteractionPolicy.logsContainerEdit(tier)) {
            staffMode.logStaffAction(player, "container-edit", top.getType() + " drag");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand) {
            handleContainerEntity(player, event, event.getRightClicked().getType().toString());
            return;
        }
        cancelWorldUse(player, event, "entity-interact", event.getRightClicked().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand) {
            handleContainerEntity(player, event, event.getRightClicked().getType().toString());
            return;
        }
        cancelWorldUse(player, event, "entity-interact-at", event.getRightClicked().getType().toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        if (staffMode.isUnrestricted(player)) {
            staffMode.logStaffAction(player, "armor-stand-edit",
                    event.getRightClicked().getType().toString());
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier)) {
            event.setCancelled(true);
            return;
        }
        if (StaffModeWorldInteractionPolicy.logsContainerEdit(tier)) {
            staffMode.logStaffAction(player, "armor-stand-edit",
                    event.getRightClicked().getType().toString());
        }
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

    private void handleContainerEntity(Player player, Cancellable event, String detail) {
        if (staffMode.isUnrestricted(player)) {
            staffMode.logStaffAction(player, "container-entity-edit", detail);
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier)) {
            event.setCancelled(true);
            return;
        }
        if (StaffModeWorldInteractionPolicy.logsContainerEdit(tier)) {
            staffMode.logStaffAction(player, "container-entity-edit", detail);
        }
    }

    private boolean onDuty(Player player) {
        return staffMode.active(player.getUniqueId());
    }

    private void cancelWorldUse(Player player, Cancellable event, String action, String detail) {
        if (!onDuty(player)) {
            return;
        }
        if (staffMode.isUnrestricted(player)) {
            staffMode.logStaffAction(player, action, detail);
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksWorldUse(tier)) {
            event.setCancelled(true);
        } else if (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)) {
            staffMode.logStaffAction(player, action, detail);
        }
    }

    private static boolean isContainer(Block block) {
        if (block == null) {
            return false;
        }
        if (block.getType() == Material.ENDER_CHEST) {
            return true;
        }
        try {
            BlockState state = block.getState();
            return state instanceof InventoryHolder;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isProtectedContainerInventory(Inventory inventory) {
        if (inventory == null || inventory.getType() == InventoryType.PLAYER) {
            return false;
        }
        if (inventory.getType() == InventoryType.ENDER_CHEST) {
            return true;
        }
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof BlockState) {
            return true;
        }
        try {
            return inventory.getLocation() != null;
        } catch (RuntimeException exception) {
            return false;
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
