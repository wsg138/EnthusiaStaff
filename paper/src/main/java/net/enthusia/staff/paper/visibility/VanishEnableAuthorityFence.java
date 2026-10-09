package net.enthusia.staff.paper.visibility;

import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.StaffRank;

/** Fences publication against rank changes observed during durable vanish enable. */
final class VanishEnableAuthorityFence {
    private VanishEnableAuthorityFence() { }

    static boolean eligible(StaffRank expected, StaffRank live) {
        return VanishRankReconciliationPolicy.mayVanish(expected) && expected == live;
    }

    static boolean commitIfEligible(
            StaffRank expected, Supplier<StaffRank> liveRank, Runnable commit, Runnable compensate) {
        if (!eligible(expected, liveRank.get())) {
            return false;
        }
        commit.run();
        if (!eligible(expected, liveRank.get())) {
            compensate.run();
            return false;
        }
        return true;
    }
}
