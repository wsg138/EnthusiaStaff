package net.enthusia.staff.paper.staff;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;

final class StaffModeAccessPolicy {
    private StaffModeAccessPolicy() {
    }

    static boolean blocksAllInventoryMutation(StaffRank rank) {
        return rank == StaffRank.HELPER;
    }

    static boolean blocksEnderChestOpen(StaffRank rank) {
        return rank == StaffRank.HELPER || rank == StaffRank.MOD || rank == StaffRank.DEVELOPER;
    }

    static boolean blocksEnderChestMutation(StaffRank rank) {
        return rank != StaffRank.FOUNDER;
    }

    static boolean usesCreativeMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    static GameMode requiredGameMode(StaffRank rank) {
        return usesCreativeMode(rank) ? GameMode.CREATIVE : GameMode.SPECTATOR;
    }

    static boolean hasAdvancedStaffTools(StaffRank rank) {
        return rank != StaffRank.HELPER;
    }
}
