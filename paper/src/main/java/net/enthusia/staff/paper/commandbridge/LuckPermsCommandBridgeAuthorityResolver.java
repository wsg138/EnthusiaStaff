package net.enthusia.staff.paper.commandbridge;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.CommandBridgeAuthorityResolver;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;

/** Loads the actor's current LuckPerms state immediately before command dispatch. */
public final class LuckPermsCommandBridgeAuthorityResolver implements CommandBridgeAuthorityResolver {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);

    private final LuckPerms luckPerms;

    public LuckPermsCommandBridgeAuthorityResolver(LuckPerms luckPerms) {
        if (luckPerms == null) {
            throw new IllegalArgumentException("LuckPerms is required");
        }
        this.luckPerms = luckPerms;
    }

    @Override
    public Optional<Snapshot> current(UUID actorPlayerId, String requiredPermission) {
        if (actorPlayerId == null || requiredPermission == null || requiredPermission.isBlank()) {
            throw new IllegalArgumentException("current command authority lookup is invalid");
        }
        try {
            User user = luckPerms.getUserManager().loadUser(actorPlayerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return snapshot(user, requiredPermission);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("command authority lookup interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("command authority lookup unavailable", failure);
        }
    }

    private static Optional<Snapshot> snapshot(User user, String requiredPermission) {
        CachedPermissionData permissions = user.getCachedData().getPermissionData();
        Optional<StaffRank> rank = PaperStaffRankResolver.resolve(
                permission -> permissions.checkPermission(permission).asBoolean()
        );
        return rank.map(value -> new Snapshot(
                value,
                permissions.checkPermission(requiredPermission).asBoolean()
        ));
    }
}
