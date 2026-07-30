package net.enthusia.staff.paper.visibility;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;

final class SpectatorTabPolicy {
    private SpectatorTabPolicy() {
    }

    static boolean masksSpectatorEntry(StaffRank rank) {
        return rank != null && rank != StaffRank.SYSTEM;
    }

    static boolean offersVisibilityChoice(StaffRank rank) {
        return rank == StaffRank.DEVELOPER
                || rank == StaffRank.ADMIN
                || rank == StaffRank.FOUNDER;
    }

    static boolean mayAppearNormally(
            StaffRank rank,
            GameMode gameMode,
            boolean fullyVanished,
            boolean packetMaskAvailable
    ) {
        return offersVisibilityChoice(rank)
                && gameMode == GameMode.SPECTATOR
                && !fullyVanished
                && packetMaskAvailable;
    }

    static boolean shouldList(
            StaffRank rank,
            GameMode gameMode,
            boolean canSee,
            boolean hiddenSpectator,
            boolean packetMaskAvailable
    ) {
        if (!canSee || hiddenSpectator) {
            return false;
        }
        return rank == null
                || gameMode != GameMode.SPECTATOR
                || packetMaskAvailable;
    }
}
