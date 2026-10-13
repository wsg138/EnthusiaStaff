package net.enthusia.staff.paper.staff;

import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryAction;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Tiered world-interaction enforcement for on-duty staff.
 *
 * <p>Helpers are observational: they may silently open world containers to inspect them, but
 * every inventory mutation path is cancelled while that container is open. Mod may edit
 * containers and ordinary blocks with audit logging. Developer/Admin/Founder world interactions
 * are unrestricted but audited; Developer remains separate from moderation authority.</p>
 */
public final class StaffModeWorldInteractionListener implements Listener {
    private static final String CONTAINER_EDIT_ACTION = "container-edit";
    private static final String CONTAINER_EDIT_DENIED_ACTION = "container-edit-denied";
    private static final String ARMOR_STAND_EDIT_ACTION = "armor-stand-edit";
    private static final String ARMOR_STAND_EDIT_DENIED_ACTION = "armor-stand-edit-denied";

    private final StaffModeManager staffMode;
    private final PairedEntityInteractionAudit pairedEntityAudit = new PairedEntityInteractionAudit();

    public StaffModeWorldInteractionListener(StaffModeManager staffMode) {
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    // Enforce rank restrictions before the event completes. Successful mutations are
    // recorded at MONITOR, after other plugins' cancellation handlers have run.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (onDuty(player) && !staffMode.isUnrestricted(player)) {
            denyRankedBlockEdit(player, event, event.getBlock(), "block-break");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (onDuty(player) && !staffMode.isUnrestricted(player)) {
            denyRankedBlockEdit(player, event, event.getBlockPlaced(), "block-place");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedBlockBreak(BlockBreakEvent event) {
        auditCompletedBlockEdit(event.getPlayer(), event.getBlock(), "block-break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedBlockPlace(BlockPlaceEvent event) {
        auditCompletedBlockEdit(event.getPlayer(), event.getBlockPlaced(), "block-place");
    }

    private void denyRankedBlockEdit(Player player, Cancellable event, Block block, String action) {
        if (StaffModeWorldInteractionPolicy.blocksBlockEdit(staffMode.dutyTier(player))) {
            event.setCancelled(true);
            // Denied actions remain distinct from committed edits, with no item NBT.
            staffMode.logStaffAction(player, blockEditAuditAction(action, true), describe(block));
        }
    }

    private void auditCompletedBlockEdit(Player player, Block block, String action) {
        if (!onDuty(player)) {
            return;
        }
        if (shouldAuditCompletedBlockEdit(staffMode.isUnrestricted(player), staffMode.dutyTier(player))) {
            staffMode.logStaffAction(player, action, describe(block));
        }
    }

    static boolean shouldAuditCompletedBlockEdit(boolean unrestricted, StaffDutyTier tier) {
        return unrestricted || StaffModeWorldInteractionPolicy.logsWorldInteraction(tier);
    }

    static String blockEditAuditAction(String action, boolean denied) {
        return denied ? action + "-denied" : action;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        cancelWorldUse(event.getPlayer(), event, "bucket-fill", describe(event.getBlockClicked()), true, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        cancelWorldUse(event.getPlayer(), event, "bucket-empty", describe(event.getBlockClicked()), true, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        cancelWorldUse(event.getPlayer(), event, "block-harvest", describe(event.getHarvestedBlock()), true, true);
    }

    // Only non-cancelled actual gameplay events are recorded as successful uses.
    // Enforcement (including denied-action auditing) remains at HIGHEST.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedBucketFill(PlayerBucketFillEvent event) {
        auditCompletedWorldUse(event.getPlayer(), "bucket-fill", describe(event.getBlockClicked()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedBucketEmpty(PlayerBucketEmptyEvent event) {
        auditCompletedWorldUse(event.getPlayer(), "bucket-empty", describe(event.getBlockClicked()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedHarvest(PlayerHarvestBlockEvent event) {
        auditCompletedWorldUse(event.getPlayer(), "block-harvest", describe(event.getHarvestedBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedShear(PlayerShearEntityEvent event) {
        auditCompletedWorldUse(event.getPlayer(), "entity-shear",
                event.getEntity().getType().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedConsume(PlayerItemConsumeEvent event) {
        auditCompletedWorldUse(event.getPlayer(), "item-consume",
                event.getItem().getType().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedFish(PlayerFishEvent event) {
        auditCompletedWorldUse(event.getPlayer(), "fishing", event.getState().toString());
    }

    private void auditCompletedWorldUse(Player player, String action, String detail) {
        if (onDuty(player) && shouldAuditCompletedWorldUse(
                staffMode.isUnrestricted(player), staffMode.dutyTier(player))) {
            staffMode.logStaffAction(player, action, detail);
        }
    }

    static boolean shouldAuditCompletedWorldUse(boolean unrestricted, StaffDutyTier tier) {
        return unrestricted || (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)
                && !StaffModeWorldInteractionPolicy.blocksWorldUse(tier));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Paper/Bukkit farmland trampling is a PHYSICAL block interaction.
        // Run before the tier/unrestricted checks: passive damage is not a
        // permissible world edit for staff mode or standalone vanish.
        if (event.getAction() == Action.PHYSICAL
                && StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(
                        event.getClickedBlock() == null ? null : event.getClickedBlock().getType(),
                        staffMode.protectsFarmland(player.getUniqueId()))) {
            event.setCancelled(true);
            staffMode.logStaffAction(player, "farmland-trample-denied", describe(event.getClickedBlock()));
            return;
        }
        if (!onDuty(player)) {
            return;
        }
        if (!staffMode.isUnrestricted(player)) {
            enforceRankedInteraction(player, event);
        }
    }

    // Successful block interactions and permitted HELPER container views belong at
    // MONITOR. A later protection plugin may cancel a still-allowed HIGHEST event.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        Action action = event.getAction();
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (!staffMode.isUnrestricted(player)
                && shouldAuditCompletedContainerView(tier, action,
                        isContainer(event.getClickedBlock()))) {
            staffMode.logStaffAction(player, "container-view", describe(event.getClickedBlock()));
            return;
        }
        if (shouldAuditCompletedBlockInteraction(staffMode.isUnrestricted(player), tier, action)) {
            staffMode.logStaffAction(player, "block-interact",
                    action + " " + describe(event.getClickedBlock()));
        }
    }

    static boolean shouldAuditCompletedContainerView(StaffDutyTier tier, Action action,
                                                     boolean container) {
        return action == Action.RIGHT_CLICK_BLOCK
                && container
                && StaffModeWorldInteractionPolicy.allowsContainerView(tier);
    }

    static boolean shouldAuditCompletedBlockInteraction(boolean unrestricted,
                                                        StaffDutyTier tier, Action action) {
        if (action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR) {
            return false;
        }
        return unrestricted || (StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)
                && !StaffModeWorldInteractionPolicy.blocksBlockInteraction(tier, action));
    }

    private void enforceRankedInteraction(Player player, PlayerInteractEvent event) {
        Action action = event.getAction();
        StaffDutyTier tier = staffMode.dutyTier(player);
        // Allowed HELPER container views are audited at MONITOR after cancellation.
        if (shouldAuditCompletedContainerView(tier, action, isContainer(event.getClickedBlock()))) {
            return;
        }
        if (StaffModeWorldInteractionPolicy.blocksBlockInteraction(tier, action)) {
            event.setCancelled(true);
            if (shouldAuditDeniedBlockInteraction(tier, action, event.getHand(),
                    event.getItem() == null ? null : event.getItem().getType(),
                    isContainer(event.getClickedBlock()))) {
                // Only a meaningful main-hand block use: do not duplicate the
                // separate bucket, container, block-break, or offhand audits.
                staffMode.logStaffAction(player, "block-interact-denied",
                        action + " " + describe(event.getClickedBlock()));
            }
            return;
        }
    }

    /**
     * A denied right-click block use would otherwise leave no audit event.
     * Block-break/place, bucket and protected-container paths have their own
     * audit IDs, so avoid duplicating those or the secondary offhand event.
     */
    static boolean shouldAuditDeniedBlockInteraction(
            StaffDutyTier tier, Action action, EquipmentSlot hand, Material heldItem, boolean container
    ) {
        return StaffModeWorldInteractionPolicy.blocksBlockInteraction(tier, action)
                && action == Action.RIGHT_CLICK_BLOCK
                && hand == EquipmentSlot.HAND
                && !container
                && (heldItem == null || (heldItem != Material.BUCKET
                        && !heldItem.name().endsWith("_BUCKET")));
    }

    // Defensive fallback for implementations that notify an entity-caused block
    // change without exposing the corresponding PHYSICAL interaction. Only a
    // player's attempted farmland conversion is affected.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandChange(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(
                event.getBlock().getType(), staffMode.protectsFarmland(player.getUniqueId()))
                && event.getTo() == Material.DIRT) {
            event.setCancelled(true);
            staffMode.logStaffAction(player, "farmland-trample-denied", describe(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerEdit(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !onDuty(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isProtectedContainerInventory(top) || staffMode.isUnrestricted(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEdit(tier)) {
            // Cancel lower-inventory transfers too: shift-click and collect can mutate the top.
            event.setCancelled(true);
            if (shouldAuditDeniedContainerClick(event.getAction(),
                    event.getRawSlot(), top.getSize())) {
                staffMode.logStaffAction(player, CONTAINER_EDIT_DENIED_ACTION,
                        top.getType() + " raw-slot=" + event.getRawSlot()
                                + " action=" + event.getAction());
            } else if (shouldAuditDeniedPlayerInventoryClick(tier, event.getAction(),
                    event.getRawSlot(), top.getSize(), true)) {
                // The generic manager is registered later and ignores this cancelled
                // event, so record a meaningful HELPER lower-inventory denial here.
                staffMode.logStaffAction(player, "inventory-edit-denied",
                        "click=" + event.getClick() + " action=" + event.getAction()
                                + " raw-slot=" + event.getRawSlot());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedContainerEdit(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !onDuty(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (isProtectedContainerInventory(top)
                && shouldAuditCompletedContainerClick(event.getAction(),
                        event.getRawSlot(), top.getSize())
                && shouldAuditCompletedContainerEdit(staffMode.isUnrestricted(player),
                        staffMode.dutyTier(player))) {
            // A later StaffModeManager or third-party handler may deny the edit.
            // Only non-cancelled attempts that can mutate the top container are audited.
            staffMode.logStaffAction(player, CONTAINER_EDIT_ACTION,
                    top.getType() + " raw-slot=" + event.getRawSlot()
                            + " action=" + event.getAction()
                            + " item=" + StaffModeManager.describe(event.getCurrentItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !onDuty(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isProtectedContainerInventory(top) || staffMode.isUnrestricted(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEdit(tier)) {
            event.setCancelled(true);
            if (touchesProtectedContainerSlot(event.getRawSlots(), top.getSize())) {
                staffMode.logStaffAction(player, CONTAINER_EDIT_DENIED_ACTION,
                        top.getType() + " drag");
            } else if (shouldAuditDeniedPlayerInventoryDrag(tier,
                    event.getRawSlots(), top.getSize(), true)) {
                staffMode.logStaffAction(player, "inventory-edit-denied",
                        "drag affected-slots=" + event.getRawSlots().size());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedContainerDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !onDuty(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (isProtectedContainerInventory(top)
                && touchesProtectedContainerSlot(event.getRawSlots(), top.getSize())
                && shouldAuditCompletedContainerEdit(staffMode.isUnrestricted(player),
                        staffMode.dutyTier(player))) {
            staffMode.logStaffAction(player, CONTAINER_EDIT_ACTION,
                    top.getType() + " drag cursor=" + StaffModeManager.describe(event.getOldCursor()));
        }
    }

    static boolean shouldAuditCompletedContainerEdit(boolean unrestricted, StaffDutyTier tier) {
        return unrestricted || StaffModeWorldInteractionPolicy.logsContainerEdit(tier);
    }

    /**
     * Lower-inventory normal clicks are blocked by the existing safety policy, but
     * are not evidence of an attempted container mutation. Only record clicks
     * directly targeting the protected top inventory or transfer/collect actions.
     */
    static boolean shouldAuditDeniedContainerClick(InventoryAction action, int rawSlot, int topSize) {
        if (action == null || action == InventoryAction.NOTHING) {
            return false;
        }
        return (rawSlot >= 0 && rawSlot < topSize)
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    // Reuse the denied-attempt filter: ordinary clicks in the player's lower
    // inventory and no-op actions do not demonstrate a container edit.
    // Shift-click and collect-to-cursor can mutate the top from a lower slot.
    static boolean shouldAuditCompletedContainerClick(InventoryAction action, int rawSlot, int topSize) {
        return shouldAuditDeniedContainerClick(action, rawSlot, topSize);
    }

    /**
     * Separate HELPER's ordinary player-inventory denial from a real attempt
     * to mutate the protected top container. Container edits already have a
     * distinct event owner and should never create a second denied record.
     */
    static boolean shouldAuditDeniedPlayerInventoryClick(StaffDutyTier tier,
            InventoryAction action, int rawSlot, int topSize, boolean protectedTop) {
        return tier == StaffDutyTier.HELPER
                && action != null && action != InventoryAction.NOTHING
                && rawSlot >= 0
                && (!protectedTop || (rawSlot >= topSize
                        && !shouldAuditDeniedContainerClick(action, rawSlot, topSize)));
    }

    static boolean shouldAuditDeniedPlayerInventoryDrag(StaffDutyTier tier,
            Set<Integer> rawSlots, int topSize, boolean protectedTop) {
        return tier == StaffDutyTier.HELPER
                && rawSlots != null && !rawSlots.isEmpty()
                && (!protectedTop || !touchesProtectedContainerSlot(rawSlots, topSize));
    }

    // When the Ender Chest view itself is rank-restricted, a normal lower
    // inventory action remains denied by StaffModeManager, but is not a
    // top-container transfer and should receive a generic denied audit.
    static boolean shouldAuditDeniedEnderLowerClick(InventoryAction action,
            int rawSlot, int topSize) {
        return action != null && action != InventoryAction.NOTHING
                && rawSlot >= topSize
                && !shouldAuditDeniedContainerClick(action, rawSlot, topSize);
    }

    static boolean touchesProtectedContainerSlot(Set<Integer> rawSlots, int topSize) {
        return rawSlots.stream().anyMatch(slot -> slot >= 0 && slot < topSize);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand) {
            handleContainerEntity(player, event, event.getRightClicked().getType().toString(),
                    event.getRightClicked() instanceof ItemFrame
                            && shouldAuditDeniedItemFrame(event.getHand()));
            return;
        }
        // Paired generic/precise variants are logged only once after cancellation.
        cancelWorldUse(player, event, "entity-interact", event.getRightClicked().getType().toString(),
                false, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand) {
            handleContainerEntity(player, event, event.getRightClicked().getType().toString(), false);
            return;
        }
        cancelWorldUse(player, event, "entity-interact-at", event.getRightClicked().getType().toString(),
                false, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedInteractEntity(PlayerInteractEntityEvent event) {
        auditCompletedEntityInteraction(event, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedInteractAtEntity(PlayerInteractAtEntityEvent event) {
        auditCompletedEntityInteraction(event, true);
    }

    private void auditCompletedEntityInteraction(PlayerInteractEntityEvent event, boolean precise) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        // Item-frame placement, removal and rotation have a dedicated Paper
        // change event; a generic right-click may do nothing and is not an edit.
        // Armor-stand equipment changes have their own final-state event too.
        if (event.getRightClicked() instanceof ItemFrame
                || event.getRightClicked() instanceof ArmorStand
                || !shouldAuditCompletedWorldUse(staffMode.isUnrestricted(player), staffMode.dutyTier(player))) {
            return;
        }
        if (pairedEntityAudit.allow(player.getUniqueId(), event.getRightClicked().getUniqueId(),
                event.getHand(), precise, System.nanoTime())) {
            staffMode.logStaffAction(player, precise ? "entity-interact-at" : "entity-interact",
                    event.getRightClicked().getType().toString());
        }
    }

    // Paper emits this only for a frame PLACE, REMOVE or ROTATE attempt. Unlike
    // generic entity interactions it excludes empty/no-op frame right-clicks.
    // MONITOR/ignoreCancelled prevents a later protection plugin's cancellation
    // from being reported as a completed change.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedItemFrameChange(PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player) || !shouldAuditCompletedContainerEntityEdit(
                staffMode.isUnrestricted(player), staffMode.dutyTier(player))) {
            return;
        }
        staffMode.logStaffAction(player, "container-entity-edit",
                event.getItemFrame().getType() + " action=" + event.getAction());
    }

    static boolean shouldAuditCompletedContainerEntityEdit(boolean unrestricted, StaffDutyTier tier) {
        return unrestricted || (StaffModeWorldInteractionPolicy.logsContainerEdit(tier)
                && !StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPairedEntityAuditQuit(PlayerQuitEvent event) {
        pairedEntityAudit.forget(event.getPlayer().getUniqueId());
    }

    /**
     * A normal interaction often emits a generic + precise event within one
     * server tick. Suppress only that cross-variant duplicate for the same
     * player, entity and hand; never coalesce two generic clicks or two precise
     * clicks. A small bounded cache avoids retaining disconnected player IDs.
     */
    static final class PairedEntityInteractionAudit {
        private static final long WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
        private static final int MAX_ACTORS = 256;
        private final Map<UUID, InteractionRecord> recent = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, InteractionRecord> eldest) {
                return size() > MAX_ACTORS;
            }
        };

        boolean allow(UUID actor, UUID entity, EquipmentSlot hand, boolean precise, long nowNanos) {
            InteractionRecord previous = recent.get(actor);
            if (previous != null
                    && previous.precise() != precise
                    && previous.entity().equals(entity)
                    && previous.hand() == hand
                    && nowNanos - previous.atNanos() >= 0
                    && nowNanos - previous.atNanos() <= WINDOW_NANOS) {
                return false;
            }
            recent.put(actor, new InteractionRecord(entity, hand, precise, nowNanos));
            return true;
        }

        void forget(UUID actor) {
            recent.remove(actor);
        }

        int trackedActors() {
            return recent.size();
        }

        private record InteractionRecord(UUID entity, EquipmentSlot hand, boolean precise, long atNanos) { }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player) || staffMode.isUnrestricted(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier)) {
            event.setCancelled(true);
            // Only the dedicated manipulation event records this denied edit.
            // General entity-interact variants can both fire for one click.
            staffMode.logStaffAction(player, armorStandEditAuditAction(tier),
                    event.getRightClicked().getType().toString());
        }
    }

    // An allowed armor-stand equipment mutation must survive other protection
    // plugins' cancellation handlers before it is recorded as successful.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompletedArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (!onDuty(player)) {
            return;
        }
        if (shouldAuditCompletedArmorStandManipulate(
                staffMode.isUnrestricted(player), staffMode.dutyTier(player))) {
            staffMode.logStaffAction(player, ARMOR_STAND_EDIT_ACTION,
                    event.getRightClicked().getType().toString());
        }
    }

    static boolean shouldAuditCompletedArmorStandManipulate(boolean unrestricted, StaffDutyTier tier) {
        return unrestricted || (StaffModeWorldInteractionPolicy.logsContainerEdit(tier)
                && !StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier));
    }

    static String armorStandEditAuditAction(StaffDutyTier tier) {
        return StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier)
                ? ARMOR_STAND_EDIT_DENIED_ACTION : ARMOR_STAND_EDIT_ACTION;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        cancelWorldUse(event.getPlayer(), event, "entity-shear",
                event.getEntity().getType().toString(), true, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        cancelWorldUse(event.getPlayer(), event, "item-consume",
                event.getItem().getType().toString(), true, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        cancelWorldUse(event.getPlayer(), event, "fishing", event.getState().toString(), true, true);
    }

    // An entity click may also dispatch PlayerInteractAtEntityEvent. Only the generic
    // main-hand item-frame interaction may record a denied edit; the precise variant
    // still cancels, but never records the same attempt twice.
    static boolean shouldAuditDeniedItemFrame(EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND;
    }

    private void handleContainerEntity(Player player, Cancellable event, String detail,
                                       boolean auditDeniedItemFrame) {
        if (staffMode.isUnrestricted(player)) {
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        if (StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(tier)) {
            event.setCancelled(true);
            if (auditDeniedItemFrame) {
                staffMode.logStaffAction(player, "item-frame-edit-denied", detail);
            }
        }
        // Successful frame edits belong to the cancellation-aware MONITOR handler.
        // Armor-stand equipment edits belong to onCompletedArmorStandManipulate.
    }

    private boolean onDuty(Player player) {
        return staffMode.active(player.getUniqueId());
    }

    private void cancelWorldUse(Player player, Cancellable event, String action,
                                String detail, boolean auditDenied) {
        cancelWorldUse(player, event, action, detail, auditDenied, false);
    }

    private void cancelWorldUse(Player player, Cancellable event, String action,
                                String detail, boolean auditDenied, boolean deferSuccessfulAudit) {
        if (!onDuty(player)) {
            return;
        }
        if (staffMode.isUnrestricted(player)) {
            if (!deferSuccessfulAudit) {
                staffMode.logStaffAction(player, action, detail);
            }
            return;
        }
        StaffDutyTier tier = staffMode.dutyTier(player);
        boolean denied = StaffModeWorldInteractionPolicy.blocksWorldUse(tier);
        if (denied) {
            event.setCancelled(true);
        }
        Optional<String> auditedAction = worldUseAuditAction(tier, action, auditDenied);
        if (auditedAction.isPresent() && (denied || !deferSuccessfulAudit)) {
            staffMode.logStaffAction(player, auditedAction.orElseThrow(), detail);
        }
    }

    // Keep the logging decision tied to the same tier policy that enforces the
    // denial. Entity-interact variants opt out until duplicate events are handled.
    static Optional<String> worldUseAuditAction(StaffDutyTier tier, String action, boolean auditDenied) {
        if (StaffModeWorldInteractionPolicy.blocksWorldUse(tier)) {
            return auditDenied ? Optional.of(action + "-denied") : Optional.empty();
        }
        return StaffModeWorldInteractionPolicy.logsWorldInteraction(tier)
                ? Optional.of(action) : Optional.empty();
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

    // Shared by StaffModeManager so one permitted top-container edit has one audit owner.
    static boolean isProtectedContainerInventory(Inventory inventory) {
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
