package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class PolarSpectatorEligibilitySnapshotTest {
    @Test
    void timestampRefreshAloneDoesNotLookLikeAnEligibilityTransition() {
        var first = snapshot(GameMode.SPECTATOR, StaffRank.FOUNDER, StaffRank.FOUNDER, null, true, true, 10L);
        var refreshed = snapshot(GameMode.SPECTATOR, StaffRank.FOUNDER, StaffRank.FOUNDER, null, true, true, 20L);

        assertTrue(first.sameSemanticState(refreshed));
    }

    @Test
    void losingPermanentIdentityWhileSpectatingIsVisibleInSnapshotState() {
        var before = snapshot(GameMode.SPECTATOR, StaffRank.FOUNDER, StaffRank.FOUNDER, null, true, true, 10L);
        var after = snapshot(GameMode.SPECTATOR, null, null, null, true, false, 20L);

        assertFalse(before.sameSemanticState(after));
        assertTrue(after.hasStaffSignal());
    }

    private static PolarSpectatorPhaseCompatibility.EligibilitySnapshot snapshot(
            GameMode gameMode,
            StaffRank resolved,
            StaffRank identity,
            StaffRank legacy,
            boolean unrestricted,
            boolean eligible,
            long refreshedAtNanos
    ) {
        return new PolarSpectatorPhaseCompatibility.EligibilitySnapshot(
                gameMode,
                resolved,
                identity,
                legacy,
                unrestricted,
                eligible,
                refreshedAtNanos
        );
    }
}
