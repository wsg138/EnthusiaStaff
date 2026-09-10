package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class AuthorityRankResolverTest {
    @Test
    void resolvesHighestConfiguredModerationRank() {
        Set<String> permissions = Set.of(
                "enthusiastaff.rank.helper",
                "enthusiastaff.rank.mod",
                "enthusiastaff.rank.admin"
        );

        assertEquals(
                StaffRank.ADMIN,
                AuthorityRankResolver.resolve(permissions::contains).orElseThrow()
        );
    }

    @Test
    void developerRemainsSeparateFromModerationHierarchy() {
        Set<String> permissions = Set.of(
                "enthusiastaff.rank.developer",
                "enthusiastaff.rank.admin"
        );

        assertEquals(
                StaffRank.DEVELOPER,
                AuthorityRankResolver.resolve(permissions::contains).orElseThrow()
        );
    }

    @Test
    void absentStaffPermissionProducesNoAuthority() {
        assertTrue(AuthorityRankResolver.resolve(ignored -> false).isEmpty());
    }
}
