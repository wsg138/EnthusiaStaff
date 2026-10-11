package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class StaffModeWorldInteractionPolicyTest {
    private static final String BUCKET_FILL_ACTION = "bucket-fill";
    @Test
    void farmlandTramplingIsDeniedForAnyProtectedStaffOrVanishedPlayer() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(Material.FARMLAND, true));
        assertFalse(StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(Material.FARMLAND, false));
        assertFalse(StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(Material.WHEAT, true));
        assertFalse(StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(Material.DIRT, true));
        assertFalse(StaffModeWorldInteractionPolicy.blocksFarmlandTrampling(null, true));
    }


    @Test
    void rejectedBlockEditsHaveDistinctAuditActionNames() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.HELPER));
        assertEquals("block-break-denied",
                StaffModeWorldInteractionListener.blockEditAuditAction("block-break", true));
        assertEquals("block-place-denied",
                StaffModeWorldInteractionListener.blockEditAuditAction("block-place", true));
        assertEquals("block-break",
                StaffModeWorldInteractionListener.blockEditAuditAction("block-break", false));
        assertEquals("block-place",
                StaffModeWorldInteractionListener.blockEditAuditAction("block-place", false));
    }

    @Test
    void deniedWorldUseAuditsMeaningfulAttemptsWithoutChangingTierPolicy() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.ADMIN));
        for (StaffDutyTier tier : java.util.List.of(StaffDutyTier.HELPER, StaffDutyTier.MOD)) {
            assertEquals(Optional.of("bucket-fill-denied"),
                    StaffModeWorldInteractionListener.worldUseAuditAction(tier, BUCKET_FILL_ACTION, true));
            assertEquals(Optional.of("block-harvest-denied"),
                    StaffModeWorldInteractionListener.worldUseAuditAction(tier, "block-harvest", true));
            assertEquals(Optional.of("entity-shear-denied"),
                    StaffModeWorldInteractionListener.worldUseAuditAction(tier, "entity-shear", true));
            assertEquals(Optional.of("item-consume-denied"),
                    StaffModeWorldInteractionListener.worldUseAuditAction(tier, "item-consume", true));
            assertEquals(Optional.of("fishing-denied"),
                    StaffModeWorldInteractionListener.worldUseAuditAction(tier, "fishing", true));
            assertEquals(Optional.empty(), StaffModeWorldInteractionListener.worldUseAuditAction(
                    tier, "entity-interact", false), "avoid duplicate entity-interact audits");
        }
        assertEquals(Optional.of(BUCKET_FILL_ACTION),
                StaffModeWorldInteractionListener.worldUseAuditAction(StaffDutyTier.ADMIN,
                        BUCKET_FILL_ACTION, true));
        assertEquals(Optional.of(BUCKET_FILL_ACTION),
                StaffModeWorldInteractionListener.worldUseAuditAction(StaffDutyTier.DEVELOPER,
                        BUCKET_FILL_ACTION, true));
        assertEquals(Optional.empty(), StaffModeWorldInteractionListener.worldUseAuditAction(
                null, "entity-interact", false));
        assertEquals(Optional.of("fishing-denied"), StaffModeWorldInteractionListener.worldUseAuditAction(
                null, "fishing", true));
    }

    @Test
    void deniedContainerAuditTracksMutationsNotOrdinaryPlayerInventoryClicks() {
        int topSize = 27;
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.PICKUP_ALL, 3, topSize));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.PLACE_ALL, 12, topSize));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.PICKUP_ALL, 32, topSize));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.NOTHING, 3, topSize));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                null, 3, topSize));
    }

    @Test
    void deniedContainerAuditIncludesTransferAndProtectedDragAttempts() {
        int topSize = 27;
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.MOVE_TO_OTHER_INVENTORY, 33, topSize));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.COLLECT_TO_CURSOR, 33, topSize));
        assertFalse(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.HOTBAR_SWAP, 32, topSize));
        assertTrue(StaffModeWorldInteractionListener.shouldAuditDeniedContainerClick(
                InventoryAction.HOTBAR_SWAP, 8, topSize));
        assertFalse(StaffModeWorldInteractionListener.touchesProtectedContainerSlot(
                Set.of(27, 28, 35), topSize));
        assertTrue(StaffModeWorldInteractionListener.touchesProtectedContainerSlot(
                Set.of(27, 8, 35), topSize));
    }

    @Test
    void helperTierKeepsFullLockdown() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.HELPER));
        assertFalse(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.HELPER));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.HELPER, Action.LEFT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.HELPER, Action.RIGHT_CLICK_BLOCK));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.HELPER, Action.PHYSICAL));
    }

    @Test
    void modTierIsLoggedNotBlockedForBlockEditsAndInteractions() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.MOD));
        assertTrue(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.MOD));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.LEFT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.RIGHT_CLICK_BLOCK));
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                StaffDutyTier.MOD, Action.PHYSICAL));
    }

    @Test
    void developerRankMapsToDedicatedTechnicalTier() {
        assertEquals(StaffDutyTier.DEVELOPER, StaffDutyTier.of(StaffRank.DEVELOPER));
    }

    @Test
    void developerTierIsUnrestrictedButLoggedForTechnicalTesting() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.DEVELOPER));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.allowsContainerView(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.DEVELOPER));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.DEVELOPER));
        assertTrue(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.DEVELOPER));
        for (Action action : Action.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(StaffDutyTier.DEVELOPER, action));
        }
    }

    @Test
    void adminTierIsUnrestrictedButLogged() {
        assertFalse(StaffModeWorldInteractionPolicy.blocksBlockEdit(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksWorldUse(StaffDutyTier.ADMIN));
        assertTrue(StaffModeWorldInteractionPolicy.logsWorldInteraction(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEdit(StaffDutyTier.ADMIN));
        assertFalse(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(StaffDutyTier.ADMIN));
        assertTrue(StaffModeWorldInteractionPolicy.logsContainerEdit(StaffDutyTier.ADMIN));
        for (Action action : Action.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(StaffDutyTier.ADMIN, action));
        }
    }

    @Test
    void unresolvedTierFailsClosed() {
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockEdit(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksWorldUse(null));
        assertFalse(StaffModeWorldInteractionPolicy.logsWorldInteraction(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEdit(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksContainerEntityEdit(null));
        assertFalse(StaffModeWorldInteractionPolicy.logsContainerEdit(null));
        assertTrue(StaffModeWorldInteractionPolicy.blocksBlockInteraction(null, Action.RIGHT_CLICK_BLOCK));
    }

    @Test
    void everyTierKeepsAirClicksForStaffTools() {
        for (StaffDutyTier tier : StaffDutyTier.values()) {
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                    tier, Action.LEFT_CLICK_AIR));
            assertFalse(StaffModeWorldInteractionPolicy.blocksBlockInteraction(
                    tier, Action.RIGHT_CLICK_AIR));
        }
    }
}
