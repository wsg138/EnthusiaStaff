package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class PaperStaffRankResolverTest {
    @Test
    void permanentIdentityResolvesWithoutLegacyAuthorityBundle() {
        assertEquals(
                StaffRank.FOUNDER,
                PaperStaffRankResolver.resolve(Set.of("enthusiastaff.identity.owner")::contains).orElseThrow()
        );
        assertEquals(
                StaffRank.ADMIN,
                PaperStaffRankResolver.resolve(Set.of("enthusiastaff.identity.admin")::contains).orElseThrow()
        );
        assertEquals(
                StaffRank.MOD,
                PaperStaffRankResolver.resolve(Set.of("enthusiastaff.identity.mod")::contains).orElseThrow()
        );
        assertEquals(
                StaffRank.HELPER,
                PaperStaffRankResolver.resolve(Set.of("enthusiastaff.identity.helper")::contains).orElseThrow()
        );
        assertEquals(
                StaffRank.DEVELOPER,
                PaperStaffRankResolver.resolve(Set.of("enthusiastaff.identity.developer")::contains).orElseThrow()
        );
    }

    @Test
    void identityWinsOverConflictingLegacyBundle() {
        Set<String> permissions = Set.of(
                "enthusiastaff.identity.helper",
                "enthusiastaff.rank.admin"
        );

        assertEquals(StaffRank.HELPER, PaperStaffRankResolver.resolve(permissions::contains).orElseThrow());
    }

    @Test
    void legacyRankStillWorksDuringMigration() {
        assertEquals(
                StaffRank.MOD,
                PaperStaffRankResolver.resolve(Set.of("enthusiastaff.rank.mod")::contains).orElseThrow()
        );
        assertTrue(PaperStaffRankResolver.resolveIdentity(Set.of("enthusiastaff.rank.mod")::contains).isEmpty());
    }

    @Test
    void unrelatedPermissionsDoNotCreateStaffIdentity() {
        assertTrue(PaperStaffRankResolver.resolve(Set.of("enthusiastaff.staffmode")::contains).isEmpty());
    }
}
