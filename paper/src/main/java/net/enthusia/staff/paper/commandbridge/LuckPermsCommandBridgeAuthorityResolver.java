package net.enthusia.staff.paper.commandbridge;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
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
    private final Predicate<UUID> activeDuty;

    public LuckPermsCommandBridgeAuthorityResolver(LuckPerms luckPerms, Predicate<UUID> activeDuty) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "LuckPerms is required");
        this.activeDuty = Objects.requireNonNull(activeDuty, "active Staff Mode gate is required");
    }

    @Override
    public Optional<Snapshot> current(UUID actorPlayerId, String requiredPermission) {
        if (actorPlayerId == null || requiredPermission == null || requiredPermission.isBlank()) {
            throw new IllegalArgumentException("current command authority lookup is invalid");
        }
        try {
            if (!activeDuty.test(actorPlayerId)) {
                return Optional.empty();
            }
            User user = luckPerms.getUserManager().loadUser(actorPlayerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return activeDuty.test(actorPlayerId) ? snapshot(user, requiredPermission) : Optional.empty();
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
