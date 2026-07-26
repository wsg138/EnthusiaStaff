package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class PaperActorResolverTest {
    @Test
    void explicitDeveloperWinsOverStaleModeratorAndAdminGrants() {
        Set<String> permissions = Set.of(
                "enthusiastaff.rank.mod",
                "enthusiastaff.rank.developer",
                "enthusiastaff.rank.admin"
        );

        assertEquals(
                StaffRank.DEVELOPER,
                PaperActorResolver.rank(permissions::contains).orElseThrow()
        );
    }

    @Test
    void unrankedPlayerDoesNotImplicitlyBecomeModerator() {
        assertTrue(PaperActorResolver.rank(ignored -> false).isEmpty());
    }

    @Test
    void founderGrantRetainsOwnerRecoveryAuthority() {
        Set<String> permissions = Set.of(
                "enthusiastaff.rank.developer",
                "enthusiastaff.rank.founder"
        );

        assertEquals(
                StaffRank.FOUNDER,
                PaperActorResolver.rank(permissions::contains).orElseThrow()
        );
    }
}
