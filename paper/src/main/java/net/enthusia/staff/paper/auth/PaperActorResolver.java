package net.enthusia.staff.paper.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
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
        return rank(sender::hasPermission)
                .map(value -> new Actor(player.getUniqueId(), sender.getName(), value));
    }

    static Optional<StaffRank> rank(Predicate<String> hasPermission) {
        if (hasPermission.test("enthusiastaff.rank.founder")) {
            return Optional.of(StaffRank.FOUNDER);
        }
        // A stale moderator/admin grant must not turn an explicitly tagged Developer into a moderator.
        if (hasPermission.test("enthusiastaff.rank.developer")) {
            return Optional.of(StaffRank.DEVELOPER);
        }
        if (hasPermission.test("enthusiastaff.rank.admin")) {
            return Optional.of(StaffRank.ADMIN);
        }
        if (hasPermission.test("enthusiastaff.rank.mod")) {
            return Optional.of(StaffRank.MOD);
        }
        return Optional.empty();
    }
}
