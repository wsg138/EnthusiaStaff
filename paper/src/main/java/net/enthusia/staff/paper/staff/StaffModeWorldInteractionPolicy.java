package net.enthusia.staff.paper.staff;

import java.util.Objects;
import org.bukkit.event.block.Action;

/**
 * Tiered world-interaction decisions for on-duty staff (overnight permission model).
 *
 * <ul>
 *   <li>HELPER — full lockdown: every world mutation and block interaction is blocked.</li>
 *   <li>MOD — Helper base plus logged-not-blocked extras: block placement/breaking, item
 *   pickup/drop, and container/block interaction are allowed but written to the staff audit
 *   log. Other world uses (buckets, harvest, shearing, fishing, consuming, entity
 *   manipulation) stay blocked like Helper.</li>
 *   <li>ADMIN — unrestricted, but every interaction is still written to the staff audit log.</li>
 * </ul>
 *
 * <p>A {@code null} tier (rank unknown or a mode transition in progress) fails closed: block.
 * Air clicks stay available for every tier so dedicated staff tools keep their normal
 * interaction path without enabling block, entity, resource or consumption actions.
 */
final class StaffModeWorldInteractionPolicy {
    private StaffModeWorldInteractionPolicy() {
    }

    /** Whether block placement/breaking must be cancelled for the tier. */
    static boolean blocksBlockEdit(StaffDutyTier tier) {
        return tier == null || tier == StaffDutyTier.HELPER;
    }

    /**
     * Whether general world uses (buckets, harvesting, shearing, fishing, consuming,
     * entity/armor-stand manipulation) must be cancelled. Mod keeps the Helper-base block here;
     * only the ADMIN tier is unrestricted.
     */
    static boolean blocksWorldUse(StaffDutyTier tier) {
        return tier == null || tier == StaffDutyTier.HELPER || tier == StaffDutyTier.MOD;
    }

    /** Whether an allowed world interaction must be written to the staff audit log. */
    static boolean logsWorldInteraction(StaffDutyTier tier) {
        return tier == StaffDutyTier.MOD || tier == StaffDutyTier.ADMIN;
    }

    /** Whether a {@link PlayerInteractEvent} block interaction must be cancelled. */
    static boolean blocksBlockInteraction(StaffDutyTier tier, Action action) {
        Objects.requireNonNull(action, "action");
        if (action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR) {
            return false;
        }
        // Helpers may open containers for viewing (silent open); Mod and Admin+ may
        // interact with the world (containers included) and every such interaction
        // is audit-logged by the caller. Non-container block interactions stay
        // blocked for Helpers.
        return tier == null || tier == StaffDutyTier.HELPER;
    }

    /**
     * Whether a Helper-tier staffer may open (but not edit) a container block.
     * Owner-mandated: Helpers get silent open + view, but no insert/remove/move.
     */
    static boolean allowsContainerView(StaffDutyTier tier) {
        return tier == StaffDutyTier.HELPER;
    }

    /**
     * Whether container inventory edits (insert/remove/move items) must be cancelled.
     * Helpers can view but never edit; Mod+ can edit but every edit is logged.
     */
    static boolean blocksContainerEdit(StaffDutyTier tier) {
        return tier == null || tier == StaffDutyTier.HELPER;
    }

    /**
     * Whether item-frame and armor-stand edits must be cancelled. These count as
     * containers: Helpers can view but not edit; Mod+ can edit but it is logged.
     */
    static boolean blocksContainerEntityEdit(StaffDutyTier tier) {
        return tier == null || tier == StaffDutyTier.HELPER;
    }

    /** Whether a container edit by an allowed tier must be written to the staff audit log. */
    static boolean logsContainerEdit(StaffDutyTier tier) {
        return tier == StaffDutyTier.MOD || tier == StaffDutyTier.ADMIN;
    }
}
