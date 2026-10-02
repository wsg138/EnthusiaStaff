package net.enthusia.staff.paper.visibility;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

interface VanishClientGameModeAdapter {
    boolean available();
    String unavailableReason();
    boolean present(Player player, GameMode gameMode);
}
