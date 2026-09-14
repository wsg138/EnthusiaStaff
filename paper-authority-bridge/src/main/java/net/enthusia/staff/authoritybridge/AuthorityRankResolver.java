package net.enthusia.staff.authoritybridge;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.StaffRank;

/** Resolves the same LuckPerms rank contract used by the full Paper runtime without player actions. */
final class AuthorityRankResolver {
    private AuthorityRankResolver() {
    }

    static Optional<StaffRank> resolve(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test("enthusiastaff.rank.founder")) {
            return Optional.of(StaffRank.FOUNDER);
        }
        if (hasPermission.test("enthusiastaff.rank.developer")) {
            return Optional.of(StaffRank.DEVELOPER);
        }
        if (hasPermission.test("enthusiastaff.rank.admin")) {
            return Optional.of(StaffRank.ADMIN);
        }
        if (hasPermission.test("enthusiastaff.rank.mod")) {
            return Optional.of(StaffRank.MOD);
        }
        if (hasPermission.test("enthusiastaff.rank.helper")) {
            return Optional.of(StaffRank.HELPER);
        }
        return Optional.empty();
    }
}
