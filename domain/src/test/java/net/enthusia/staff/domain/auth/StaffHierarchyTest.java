package net.enthusia.staff.domain.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StaffHierarchyTest {
    @Test
    void modMayChangeModAndLowerButNotAdminSanctions() {
        assertTrue(StaffHierarchy.mayMutate(StaffRank.MOD, StaffRank.HELPER, false));
        assertTrue(StaffHierarchy.mayMutate(StaffRank.MOD, StaffRank.MOD, false));
        assertTrue(StaffHierarchy.mayMutate(StaffRank.MOD, StaffRank.DEVELOPER, false));
        assertFalse(StaffHierarchy.mayMutate(StaffRank.MOD, StaffRank.ADMIN, false));
        assertFalse(StaffHierarchy.mayMutate(StaffRank.MOD, StaffRank.FOUNDER, false));
    }

    @Test
    void developerNeverReceivesModerationMutationAuthority() {
        for (StaffRank issuer : StaffRank.values()) {
            assertFalse(StaffHierarchy.mayMutate(StaffRank.DEVELOPER, issuer, false));
        }
    }

    @Test
    void bypassIsFounderOnlyAndStillCannotMutateSystemSanctions() {
        assertFalse(StaffHierarchy.mayMutate(StaffRank.ADMIN, StaffRank.FOUNDER, true));
        assertTrue(StaffHierarchy.mayMutate(StaffRank.FOUNDER, StaffRank.FOUNDER, true));
        assertFalse(StaffHierarchy.mayMutate(StaffRank.FOUNDER, StaffRank.SYSTEM, true));
    }
}
