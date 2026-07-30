package net.enthusia.staff.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultAuthorizationPolicyTest {
    private final DefaultAuthorizationPolicy policy = new DefaultAuthorizationPolicy();

    @Test
    void permissionMatrixIsExplicitForEveryRankAndMutation() {
        assertPermissions(StaffRank.DEVELOPER, EnumSet.of(
                ModerationAction.REQUEST_POLICY_SANCTION
        ));
        assertPermissions(StaffRank.HELPER, EnumSet.of(
                ModerationAction.ISSUE_POLICY_SANCTION,
                ModerationAction.REQUEST_POLICY_SANCTION
        ));
        assertPermissions(StaffRank.SYSTEM, EnumSet.of(ModerationAction.ISSUE_POLICY_SANCTION));
        assertPermissions(StaffRank.MOD, EnumSet.of(
                ModerationAction.ISSUE_POLICY_SANCTION,
                ModerationAction.REQUEST_POLICY_SANCTION,
                ModerationAction.APPROVE_POLICY_SANCTION,
                ModerationAction.LOWER_RECOMMENDATION,
                ModerationAction.END_SANCTION,
                ModerationAction.REVOKE_SANCTION,
                ModerationAction.REQUEST_FULL_OVERTURN,
                ModerationAction.ACCEPT_APPEAL,
                ModerationAction.APPLY_CASE_CONFISCATION
        ));
        assertPermissions(StaffRank.ADMIN, EnumSet.of(
                ModerationAction.ISSUE_POLICY_SANCTION,
                ModerationAction.REQUEST_POLICY_SANCTION,
                ModerationAction.APPROVE_POLICY_SANCTION,
                ModerationAction.LOWER_RECOMMENDATION,
                ModerationAction.RAISE_RECOMMENDATION,
                ModerationAction.USE_CUSTOM_DURATION,
                ModerationAction.END_SANCTION,
                ModerationAction.REVOKE_SANCTION,
                ModerationAction.FULL_OVERTURN,
                ModerationAction.REQUEST_FULL_OVERTURN,
                ModerationAction.APPROVE_OVERTURN,
                ModerationAction.ACCEPT_APPEAL,
                ModerationAction.APPLY_CASE_CONFISCATION,
                ModerationAction.MODIFY_MARKET_RESTRICTION,
                ModerationAction.MODIFY_REPUTATION_RESTRICTION
        ));
        assertPermissions(StaffRank.FOUNDER, EnumSet.allOf(ModerationAction.class));
    }

    @Test
    void helperIsBelowModeratorAndDeveloperIsOutsideApprovalHierarchy() {
        assertFalse(StaffRank.HELPER.atLeast(StaffRank.MOD));
        assertTrue(StaffRank.MOD.atLeast(StaffRank.HELPER));
        assertFalse(StaffRank.DEVELOPER.atLeast(StaffRank.MOD));
        assertFalse(StaffRank.DEVELOPER.canApprovePunishmentRequests());
        assertTrue(StaffRank.MOD.canApprovePunishmentRequests());
        assertTrue(StaffRank.ADMIN.canApprovePunishmentRequests());
        assertTrue(StaffRank.FOUNDER.canApprovePunishmentRequests());
    }

    private void assertPermissions(StaffRank rank, Set<ModerationAction> expected) {
        Actor actor = new Actor(UUID.randomUUID(), rank.name(), rank);
        for (ModerationAction action : ModerationAction.values()) {
            assertEquals(
                    expected.contains(action),
                    policy.permits(actor, action),
                    rank + " / " + action
            );
        }
    }
}
