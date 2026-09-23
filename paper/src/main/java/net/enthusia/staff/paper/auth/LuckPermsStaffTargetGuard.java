package net.enthusia.staff.paper.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffTargetHierarchyPolicy;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckPermsStaffTargetGuard implements StaffTargetGuard {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final String PROTECTED_MESSAGE =
            "Staff hierarchy blocks targeting staff at an equal or higher authority level.";
    private static final String UNAVAILABLE_MESSAGE =
            "Staff rank verification is unavailable; no action was taken.";

    private final TargetRankLookup ranks;
    private final StaffTargetHierarchyPolicy hierarchy;
    private final Logger logger;

    private LuckPermsStaffTargetGuard(
            TargetRankLookup ranks,
            StaffTargetHierarchyPolicy hierarchy,
            Logger logger
    ) {
        this.ranks = java.util.Objects.requireNonNull(ranks, "ranks");
        this.hierarchy = java.util.Objects.requireNonNull(hierarchy, "hierarchy");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    public static StaffTargetGuard discover(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must be present");
        }
        Logger logger = plugin.getLogger();
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("LuckPerms is absent; staff-target hierarchy checks will fail closed.");
            }
            return unavailable(logger);
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            return new LuckPermsStaffTargetGuard(
                    playerId -> resolve(luckPerms, playerId),
                    new StaffTargetHierarchyPolicy(),
                    logger
            );
        } catch (IllegalStateException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "LuckPerms staff-target authority is unavailable", exception);
            }
            return unavailable(logger);
        }
    }

    static StaffTargetGuard forTesting(TargetRankLookup ranks, Logger logger) {
        return new LuckPermsStaffTargetGuard(ranks, new StaffTargetHierarchyPolicy(), logger);
    }

    private static StaffTargetGuard unavailable(Logger logger) {
        return new LuckPermsStaffTargetGuard(
                ignored -> {
                    throw new IllegalStateException("LuckPerms is unavailable");
                },
                new StaffTargetHierarchyPolicy(),
                logger
        );
    }

    @Override
    public Result check(Actor actor, UUID targetId, boolean systemActor) {
        if (systemActor) {
            return Result.allow();
        }
        if (actor == null || targetId == null) {
            return Result.deny(UNAVAILABLE_MESSAGE);
        }
        try {
            StaffRank actorRank = ranks.resolve(actor.id()).orElse(null);
            if (actorRank == null) {
                return Result.deny(UNAVAILABLE_MESSAGE);
            }
            StaffRank targetRank = ranks.resolve(targetId).orElse(null);
            return hierarchy.permits(actorRank, targetRank)
                    ? Result.allow()
                    : Result.deny(PROTECTED_MESSAGE);
        } catch (RuntimeException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Staff-target rank verification failed", exception);
            }
            return Result.deny(UNAVAILABLE_MESSAGE);
        }
    }

    private static Optional<StaffRank> resolve(LuckPerms luckPerms, UUID playerId) {
        try {
            User user = luckPerms.getUserManager().loadUser(playerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return PaperStaffRankResolver.resolve(permission -> user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("staff-target rank lookup interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("staff-target rank lookup unavailable", exception);
        }
    }

    @FunctionalInterface
    interface TargetRankLookup {
        Optional<StaffRank> resolve(UUID playerId);
    }
}
