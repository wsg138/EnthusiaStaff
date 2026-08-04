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
    void vanishedRankChangesRequireDurableReplacementWhenAuthorized() {
        for (StaffRank durableRank : playerRanks()) {
            for (StaffRank liveRank : playerRanks()) {
                VanishRankReconciliationPolicy.VanishAction expected = durableRank == liveRank
                        ? VanishRankReconciliationPolicy.VanishAction.NONE
                        : VanishRankReconciliationPolicy.VanishAction.UPDATE_RANK;
                assertEquals(
                        expected,
                        VanishRankReconciliationPolicy.vanishAction(
                                true,
                                durableRank,
                                liveRank,
                                VanishRankReconciliationPolicy.StaffModeState.ACTIVE
                        )
                );
            }
        }
    }

    @Test
    void lowerRankWithUnknownSessionDefersToDurableVerification() {
        for (StaffRank rank : staffModeRanks()) {
            assertEquals(
                    VanishRankReconciliationPolicy.VanishAction.VERIFY_SESSION,
                    VanishRankReconciliationPolicy.vanishAction(
                            true,
                            rank,
                            rank,
                            VanishRankReconciliationPolicy.StaffModeState.UNKNOWN
                    )
            );
        }
    }

    @Test
    void confirmedInactiveOrCompletedExitDisablesLowerRankVanish() {
        for (StaffRank rank : staffModeRanks()) {
            assertEquals(
                    VanishRankReconciliationPolicy.VanishAction.DISABLE,
                    VanishRankReconciliationPolicy.vanishAction(
                            true,
                            rank,
                            rank,
                            VanishRankReconciliationPolicy.StaffModeState.INACTIVE
                    )
            );
            assertEquals(
                    VanishRankReconciliationPolicy.VanishAction.DISABLE,
                    VanishRankReconciliationPolicy.vanishAction(
                            true,
                            rank,
                            rank,
                            VanishRankReconciliationPolicy.StaffModeState.EXITED
                    )
            );
        }
    }

    @Test
    void independentRanksIgnoreStaffSessionState() {
        for (StaffRank rank : new StaffRank[]{StaffRank.ADMIN, StaffRank.FOUNDER}) {
            for (VanishRankReconciliationPolicy.StaffModeState state
                    : VanishRankReconciliationPolicy.StaffModeState.values()) {
                assertEquals(
                        VanishRankReconciliationPolicy.VanishAction.NONE,
                        VanishRankReconciliationPolicy.vanishAction(true, rank, rank, state)
                );
            }
        }
    }

    @Test
    void rankRemovalDisablesVanishAndRetriesStaleDurableState() {
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.DISABLE,
                VanishRankReconciliationPolicy.vanishAction(
                        true,
                        StaffRank.ADMIN,
                        null,
                        VanishRankReconciliationPolicy.StaffModeState.UNKNOWN
                )
        );
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.DISABLE,
                VanishRankReconciliationPolicy.vanishAction(
                        true,
                        StaffRank.ADMIN,
                        StaffRank.SYSTEM,
                        VanishRankReconciliationPolicy.StaffModeState.UNKNOWN
                )
        );
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.DISABLE,
                VanishRankReconciliationPolicy.vanishAction(
                        false,
                        StaffRank.ADMIN,
                        null,
                        VanishRankReconciliationPolicy.StaffModeState.UNKNOWN
                )
        );
        assertEquals(
                VanishRankReconciliationPolicy.VanishAction.NONE,
                VanishRankReconciliationPolicy.vanishAction(
                        false,
                        null,
                        null,
                        VanishRankReconciliationPolicy.StaffModeState.UNKNOWN
                )
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

    private static StaffRank[] staffModeRanks() {
        return new StaffRank[]{StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER};
    }
}
