package net.enthusia.staff.velocity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;

/**
 * Resolves website reviewer authority from current LuckPerms state at the Velocity boundary.
 * Site/Access roles may discover the reviewer surface, but they are not final mutation authority.
 */
final class WebsiteReviewerAuthority {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final Set<String> KNOWN_SITE_RANKS =
            Set.of("MOD", "DEVELOPER", "ADMIN", "FOUNDER");
    private static final List<PermissionRank> IDENTITY_ORDER = List.of(
            new PermissionRank("enthusiastaff.identity.owner", StaffRank.FOUNDER),
            new PermissionRank("enthusiastaff.identity.developer", StaffRank.DEVELOPER),
            new PermissionRank("enthusiastaff.identity.admin", StaffRank.ADMIN),
            new PermissionRank("enthusiastaff.identity.mod", StaffRank.MOD),
            new PermissionRank("enthusiastaff.identity.helper", StaffRank.HELPER)
    );
    private static final List<PermissionRank> LEGACY_ORDER = List.of(
            new PermissionRank("enthusiastaff.rank.founder", StaffRank.FOUNDER),
            new PermissionRank("enthusiastaff.rank.developer", StaffRank.DEVELOPER),
            new PermissionRank("enthusiastaff.rank.admin", StaffRank.ADMIN),
            new PermissionRank("enthusiastaff.rank.mod", StaffRank.MOD),
            new PermissionRank("enthusiastaff.rank.helper", StaffRank.HELPER)
    );

    private final RankLookup ranks;

    WebsiteReviewerAuthority(RankLookup ranks) {
        this.ranks = Objects.requireNonNull(ranks, "ranks");
    }

    static WebsiteReviewerAuthority production(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            return new WebsiteReviewerAuthority(playerId -> load(luckPerms, playerId));
        } catch (IllegalStateException exception) {
            logger.warn(
                    "LuckPerms is unavailable; website reviewer authority will fail closed",
                    exception
            );
            return new WebsiteReviewerAuthority(playerId -> {
                throw new AuthorityUnavailableException("LuckPerms provider is unavailable");
            });
        }
    }

    Actor resolve(UUID actorId, String siteRank) {
        if (actorId == null || siteRank == null || !KNOWN_SITE_RANKS.contains(siteRank)) {
            throw new WebsiteApiException(
                    400,
                    "INVALID_ACTOR",
                    "The website reviewer identity is invalid"
            );
        }
        StaffRank current;
        try {
            current = ranks.current(actorId).orElseThrow(() -> new WebsiteApiException(
                    403,
                    "APPEAL_REVIEW_FORBIDDEN",
                    "The website reviewer is not current Enthusia staff"
            ));
        } catch (WebsiteApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WebsiteApiException(
                    503,
                    "REVIEW_AUTHORITY_UNAVAILABLE",
                    "Current staff authority could not be verified"
            );
        }
        return new Actor(actorId, "Website Reviewer", current);
    }

    static Optional<StaffRank> resolveRank(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        return resolveFirst(IDENTITY_ORDER, hasPermission)
                .or(() -> resolveFirst(LEGACY_ORDER, hasPermission));
    }

    private static Optional<StaffRank> load(LuckPerms luckPerms, UUID playerId) {
        try {
            User user = luckPerms.getUserManager().loadUser(playerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return resolveRank(permission -> user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthorityUnavailableException("Staff authority lookup was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AuthorityUnavailableException("Staff authority lookup is unavailable", exception);
        }
    }

    private static Optional<StaffRank> resolveFirst(
            List<PermissionRank> candidates,
            Predicate<String> hasPermission
    ) {
        return candidates.stream()
                .filter(candidate -> hasPermission.test(candidate.permission()))
                .map(PermissionRank::rank)
                .findFirst();
    }

    @FunctionalInterface
    interface RankLookup {
        Optional<StaffRank> current(UUID playerId);
    }

    private record PermissionRank(String permission, StaffRank rank) {
    }

    private static final class AuthorityUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private AuthorityUnavailableException(String message) {
            super(message);
        }

        private AuthorityUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
