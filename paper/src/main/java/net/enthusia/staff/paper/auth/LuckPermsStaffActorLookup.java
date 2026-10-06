package net.enthusia.staff.paper.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves current offline/online target staff rank for cross-platform confirmation. */
public final class LuckPermsStaffActorLookup {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);

    private LuckPermsStaffActorLookup() {
    }

    public static Function<UUID, Optional<Actor>> discover(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must be present");
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            return ignored -> {
                throw new IllegalStateException("LuckPerms is unavailable");
            };
        }
        final LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            return ignored -> {
                throw new IllegalStateException("LuckPerms is unavailable", exception);
            };
        }
        return playerId -> resolve(luckPerms, playerId);
    }

    private static Optional<Actor> resolve(LuckPerms luckPerms, UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("target player must be present");
        }
        try {
            User user = luckPerms.getUserManager().loadUser(playerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Optional<StaffRank> rank = PaperStaffRankResolver.resolve(permission -> user.getCachedData()
                    .getPermissionData().checkPermission(permission).asBoolean());
            return rank.map(value -> new Actor(playerId, playerId.toString(), value));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("staff target lookup interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("staff target lookup unavailable", exception);
        }
    }
}
