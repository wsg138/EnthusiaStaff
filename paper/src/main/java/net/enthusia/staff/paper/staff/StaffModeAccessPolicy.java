package net.enthusia.staff.paper.staff;

import net.enthusia.staff.domain.auth.StaffRank;

final class StaffModeAccessPolicy {
    private StaffModeAccessPolicy() {
    }

    static boolean blocksAllInventoryMutation(StaffRank rank) {
        return rank == StaffRank.HELPER;
    }

    static boolean blocksEnderChest(StaffRank rank) {
        return rank == StaffRank.HELPER || rank == StaffRank.MOD || rank == StaffRank.DEVELOPER;
    }

    static boolean usesCreativeMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    static boolean hasAdvancedStaffTools(StaffRank rank) {
        return rank != StaffRank.HELPER;
    }
}
