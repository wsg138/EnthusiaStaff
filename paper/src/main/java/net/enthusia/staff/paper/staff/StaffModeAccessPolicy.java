package net.enthusia.staff.paper.staff;

import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.bukkit.event.inventory.ClickType;

final class StaffModeAccessPolicy {
    private StaffModeAccessPolicy() {
    }

    static boolean blocksAllInventoryMutation(StaffRank rank) {
        return rank == null || rank == StaffRank.HELPER || rank == StaffRank.SYSTEM;
    }

    static boolean blocksEnderChestOpen(StaffRank rank) {
        return rank != StaffRank.ADMIN && rank != StaffRank.FOUNDER;
    }

    static boolean blocksEnderChestMutation(StaffRank rank) {
        return rank != StaffRank.FOUNDER;
    }

    static boolean blocksInventoryMutation(StaffRank rank, boolean enderChestView) {
        return blocksAllInventoryMutation(rank)
                || (enderChestView && blocksEnderChestMutation(rank));
    }

    static boolean blocksStaffToolTransfer(
            ClickType click,
            boolean currentItemIsStaffTool,
            boolean cursorIsStaffTool,
            boolean referencedHotbarItemIsStaffTool,
            boolean offhandItemIsStaffTool
    ) {
        Objects.requireNonNull(click, "click");
        if (currentItemIsStaffTool || cursorIsStaffTool) {
            return true;
        }
        return switch (click) {
            case NUMBER_KEY -> referencedHotbarItemIsStaffTool;
            case SWAP_OFFHAND -> offhandItemIsStaffTool;
            default -> false;
        };
    }

    static GameMode initialGameMode(StaffRank rank) {
        if (rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER) {
            return GameMode.CREATIVE;
        }
        if (rank == StaffRank.MOD || rank == StaffRank.DEVELOPER) {
            // Mods (and Developer, which rides the Mod tier) must work in Survival while on
            // duty: Spectator cannot interact with containers or items.
            return GameMode.SURVIVAL;
        }
        return GameMode.SPECTATOR;
    }

    static boolean allowsGameMode(StaffRank rank, GameMode gameMode) {
        Objects.requireNonNull(gameMode, "gameMode");
        if (rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER) {
            // Admin/Founder have unrestricted real vanilla game-mode choice while on duty.
            return true;
        }
        if (rank == StaffRank.HELPER || rank == StaffRank.MOD || rank == StaffRank.DEVELOPER) {
            // Lower staff may switch between the protected Survival profile and real Spectator.
            return gameMode == GameMode.SURVIVAL || gameMode == GameMode.SPECTATOR;
        }
        // SYSTEM and any other non-player rank keep the historical spectator-only grant.
        return rank == StaffRank.SYSTEM && gameMode == GameMode.SPECTATOR;
    }

    static GameMode reconciledGameMode(StaffRank rank, GameMode currentGameMode) {
        Objects.requireNonNull(currentGameMode, "currentGameMode");
        return allowsGameMode(rank, currentGameMode)
                ? currentGameMode
                : initialGameMode(rank);
    }

    static boolean hasAdvancedStaffTools(StaffRank rank) {
        return rank != null && rank != StaffRank.HELPER && rank != StaffRank.SYSTEM;
    }
}
