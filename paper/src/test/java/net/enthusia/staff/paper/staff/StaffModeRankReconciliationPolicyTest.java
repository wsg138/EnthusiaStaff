package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class StaffModeRankReconciliationPolicyTest {
    @Test
    void unchangedPlayerRankKeepsCurrentProfile() {
        for (StaffRank rank : playerRanks()) {
            assertEquals(
                    StaffModeRankReconciliationPolicy.Action.NONE,
                    StaffModeRankReconciliationPolicy.decide(rank, rank)
            );
        }
    }

    @Test
    void everyLivePlayerRankChangeRequiresProfileReplacement() {
        for (StaffRank cachedRank : playerRanks()) {
            for (StaffRank liveRank : playerRanks()) {
                if (cachedRank == liveRank) {
                    continue;
                }
                assertEquals(
                        StaffModeRankReconciliationPolicy.Action.APPLY_PROFILE,
                        StaffModeRankReconciliationPolicy.decide(cachedRank, liveRank)
                );
            }
        }
    }

    @Test
    void missingCachedRankStillAppliesResolvedPlayerProfile() {
        for (StaffRank liveRank : playerRanks()) {
            assertEquals(
                    StaffModeRankReconciliationPolicy.Action.APPLY_PROFILE,
                    StaffModeRankReconciliationPolicy.decide(null, liveRank)
            );
        }
    }

    @Test
    void missingOrSystemLiveRankExitsThePlayerSession() {
        for (StaffRank cachedRank : StaffRank.values()) {
            assertEquals(
                    StaffModeRankReconciliationPolicy.Action.EXIT_SESSION,
                    StaffModeRankReconciliationPolicy.decide(cachedRank, null)
            );
            assertEquals(
                    StaffModeRankReconciliationPolicy.Action.EXIT_SESSION,
                    StaffModeRankReconciliationPolicy.decide(cachedRank, StaffRank.SYSTEM)
            );
        }
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
