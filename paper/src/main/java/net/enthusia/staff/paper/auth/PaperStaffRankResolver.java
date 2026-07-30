package net.enthusia.staff.paper.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.StaffRank;

public final class PaperStaffRankResolver {
    private PaperStaffRankResolver() {
    }

    public static Optional<StaffRank> resolve(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test("enthusiastaff.rank.founder")) {
            return Optional.of(StaffRank.FOUNDER);
        }
        // Developer is a separate technical role. Stale moderation grants must not elevate it.
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
