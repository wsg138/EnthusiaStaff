package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.config.RankConfigurationLoader;
import net.enthusia.staff.paper.config.RankConfigurationSnapshot;
import net.enthusia.staff.paper.config.StaffCapability;
import org.junit.jupiter.api.Test;

class VanishCapabilityPreviewParityTest {
    @Test
    void vanishPreviewMatchesIndependentDurableReconciliationAuthority() {
        RankConfigurationSnapshot candidate;
        try (InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ranks.yml")) {
            candidate = new RankConfigurationLoader().load(resource);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }

        for (StaffRank rank : StaffRank.values()) {
            assertEquals(
                    VanishRankReconciliationPolicy.mayVanish(rank),
                    candidate.proposes(rank, StaffCapability.VANISH),
                    () -> "Vanish rank-only eligibility mismatch for " + rank
            );
        }
        assertFalse(candidate.proposes(null, StaffCapability.VANISH));
        assertFalse(VanishRankReconciliationPolicy.mayVanish(null));
        assertFalse(VanishRankReconciliationPolicy.mayVanish(StaffRank.SYSTEM));
        assertFalse(VanishRankReconciliationPolicy.mayVanish(StaffRank.HELPER));
        assertTrue(VanishRankReconciliationPolicy.mayVanish(StaffRank.MOD));
    }

    @Test
    void helperStillCannotKeepDurableVanishInAnySessionState() {
        for (VanishRankReconciliationPolicy.StaffModeState state
                : VanishRankReconciliationPolicy.StaffModeState.values()) {
            assertEquals(
                    VanishRankReconciliationPolicy.VanishAction.DISABLE,
                    VanishRankReconciliationPolicy.vanishAction(true, StaffRank.MOD,
                            StaffRank.HELPER, state, true)
            );
        }
    }
}
