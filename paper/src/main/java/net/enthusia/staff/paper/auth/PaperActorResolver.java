package net.enthusia.staff.paper.auth;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PaperActorResolver {
    private static final UUID CONSOLE_ACTOR_ID = new UUID(0L, 0L);

    private PaperActorResolver() {
    }

    public static Optional<Actor> resolve(CommandSender sender) {
        if (sender == null) {
            return Optional.empty();
        }
        if (!(sender instanceof Player player)) {
            return Optional.of(new Actor(CONSOLE_ACTOR_ID, sender.getName(), StaffRank.FOUNDER));
        }
        return PaperStaffRankResolver.resolve(sender::hasPermission)
                .map(rank -> new Actor(player.getUniqueId(), sender.getName(), rank));
    }
}
