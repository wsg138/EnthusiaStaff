package net.enthusia.staff.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaffTargetHierarchyPolicyTest {
    private final StaffTargetHierarchyPolicy policy = new StaffTargetHierarchyPolicy();

    @Test
    void ordinaryPlayersAreNotProtectedByStaffHierarchy() {
        assertTrue(policy.permits(StaffRank.HELPER, null));
    }

    @Test
    void moderationRanksMayOnlyTargetLowerRanks() {
        assertTrue(policy.permits(StaffRank.MOD, StaffRank.HELPER));
        assertTrue(policy.permits(StaffRank.ADMIN, StaffRank.MOD));
        assertFalse(policy.permits(StaffRank.MOD, StaffRank.MOD));
        assertFalse(policy.permits(StaffRank.MOD, StaffRank.ADMIN));
        assertFalse(policy.permits(StaffRank.ADMIN, StaffRank.FOUNDER));
    }

    @Test
    void technicalDeveloperRankIsNotTreatedAsModerationAuthority() {
        assertFalse(policy.permits(StaffRank.DEVELOPER, StaffRank.HELPER));
        assertFalse(policy.permits(StaffRank.ADMIN, StaffRank.DEVELOPER));
        assertTrue(policy.permits(StaffRank.FOUNDER, StaffRank.DEVELOPER));
    }

    @Test
    void founderCannotTargetAnotherFounderButSystemCan() {
        assertEquals(
                StaffTargetHierarchyPolicy.Decision.PROTECTED,
                policy.decide(StaffRank.FOUNDER, StaffRank.FOUNDER)
        );
        assertTrue(policy.permits(StaffRank.SYSTEM, StaffRank.FOUNDER));
        assertFalse(policy.permits(StaffRank.FOUNDER, StaffRank.SYSTEM));
    }
}
