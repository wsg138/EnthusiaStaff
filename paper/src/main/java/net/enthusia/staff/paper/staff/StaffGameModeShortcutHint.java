package net.enthusia.staff.paper.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.entity.Player;

/** Client debug-menu capability only; never grants operator status or command permission. */
final class StaffGameModeShortcutHint {
    private final Set<UUID> hinted = ConcurrentHashMap.newKeySet();

    void refresh(Player player, StaffRank rank) {
        UUID id = player.getUniqueId();
        if (player.isOp()) {
            hinted.remove(id);
            return;
        }
        boolean eligible = (rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER)
                && player.hasPermission("minecraft.command.gamemode");
        if (eligible) {
            player.sendOpLevel((byte) 2);
            hinted.add(id);
        } else if (hinted.remove(id)) {
            player.sendOpLevel((byte) 0);
        }
    }

    void forget(UUID playerId) {
        hinted.remove(playerId);
    }
}
