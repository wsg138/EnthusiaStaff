package net.enthusia.staff.paper.auth;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.StaffRank;

public final class PaperStaffRankResolver {
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

    private PaperStaffRankResolver() {
    }

    /**
     * Resolves permanent staff identity first, then falls back to the legacy rank bundles.
     *
     * <p>The identity nodes deliberately carry no authority themselves. This allows LuckPerms
     * groups such as {@code owner}/{@code active-owner} to keep one stable identity while active
     * permissions are moved behind Staff Mode context.</p>
     */
    public static Optional<StaffRank> resolve(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        return resolveFirst(IDENTITY_ORDER, hasPermission)
                .or(() -> resolveFirst(LEGACY_ORDER, hasPermission));
    }

    public static Optional<StaffRank> resolveIdentity(Predicate<String> hasPermission) {
        return resolveFirst(IDENTITY_ORDER, Objects.requireNonNull(hasPermission, "hasPermission"));
    }

    public static Optional<StaffRank> resolveLegacyRank(Predicate<String> hasPermission) {
        return resolveFirst(LEGACY_ORDER, Objects.requireNonNull(hasPermission, "hasPermission"));
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

    private record PermissionRank(String permission, StaffRank rank) {
    }
}
