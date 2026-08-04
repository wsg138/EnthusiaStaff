package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class VanishRankReconciliationPolicyTest {
    @Test
    void unchangedPlayerRankKeepsViewerAuthority() {
        for (StaffRank rank : playerRanks()) {
            assertEquals(
                    VanishRankReconciliationPolicy.ViewerAction.NONE,
                    VanishRankReconciliationPolicy.viewerAction(rank, rank)
            );
        }
    }

    @Test
    void everyPromotionAndDemotionUpdatesViewerAuthority() {
        for (StaffRank cachedRank : playerRanks()) {
            for (StaffRank liveRank : playerRanks()) {
                if (cachedRank == liveRank) {
                    continue;
                }
                assertEquals(
                        VanishRankReconciliationPolicy.ViewerAction.UPDATE,
                        VanishRankReconciliationPolicy.viewerAction(cachedRank, liveRank)
                );
            }
        }
    }

    @Test
    void missingOrSystemRankRemovesViewerAuthority() {
        for (StaffRank cachedRank : playerRanks()) {
            assertEquals(
                    VanishRankReconciliationPolicy.ViewerAction.REMOVE,
                    VanishRankReconciliationPolicy.viewerAction(cachedRank, null)
            );
            assertEquals(
                    VanishRankReconciliationPolicy.ViewerAction.REMOVE,
                    VanishRankReconciliationPolicy.viewerAction(cachedRank, StaffRank.SYSTEM)
            );
        }
        assertEquals(
                VanishRankReconciliationPolicy.ViewerAction.NONE,
                VanishRankReconciliationPolicy.viewerAction(null, null)
        );
    }

    @Test
    void vanishedRankChangesRequireDurableReplacement() {
        for (StaffRank durableRank : playerRanks()) {
            for (StaffRank liveRank : playerRanks()) {
                VanishRankReconciliationPolicy.VanishAction expected = durableRank == liveRank
                        ? VanishRankReconciliationPolicy.VanishAction.NONE
                        : VanishRankReconciliationPolicy.VanishAction.UPDATE_RANK;
                assertEquals(
                        expected,
                        VanishRankReconciliationPolicy.vanishAction(true, durableRank, liveRank, true)
                );
            }
        }
    }

    @Test
    void lowerRanksCannotRemainVanishedOutsideStaffMode() {
        for (StaffRank rank : new StaffRank[]{StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER}) {
            assertEquals(
                    VanishRankReconciliationPolicy.VanishAction.DISABLE,
                    VanishRankReconciliationPolicy.vanishAction(true, rank, rank, false)
            );
        }
        for (StaffRank rank : new StaffRank[]{StaffRank.ADMIN, StaffRank.FOUNDER}) {
            assertEquals(
                    VanishRankReconciliationPolicy.VanishAction.NONE,
                    VanishRankReconciliationPolicy.vanishAction(true, rank, rank, false)
            );
        }
    }

    @Test
    void rankRemovalDisablesVanishAndRetriesStaleDurableState() {
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.DISABLE,
                VanishRankReconciliationPolicy.vanishAction(true, StaffRank.ADMIN, null, false)
        );
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.DISABLE,
                VanishRankReconciliationPolicy.vanishAction(true, StaffRank.ADMIN, StaffRank.SYSTEM, false)
        );
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.DISABLE,
                VanishRankReconciliationPolicy.vanishAction(false, StaffRank.ADMIN, null, false)
        );
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.NONE,
                VanishRankReconciliationPolicy.vanishAction(false, null, null, false)
        );
    }

    private static StaffRank[] playerRanks() {
        return new StaffRank[]{
                StaffRank.HELPER,
                StaffRank.MOD,
                StaffRank.DEVELOPER,
                StaffRank.ADMIN,
                StaffRank.FOUNDER
        };
    }
}
