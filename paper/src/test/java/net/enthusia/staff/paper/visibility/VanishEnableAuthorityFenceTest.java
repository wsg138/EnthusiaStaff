package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class VanishEnableAuthorityFenceTest {
    @Test void currentCanonicalRankEligibilityIsPreserved() {
        for (StaffRank rank : StaffRank.values()) {
            assertEquals(rank != StaffRank.SYSTEM && rank != StaffRank.HELPER, VanishEnableAuthorityFence.eligible(rank, rank));
        }
    }
    @Test void revokedRankPreventsAnyWrite() {
        AtomicBoolean written = new AtomicBoolean();
        AtomicBoolean compensated = new AtomicBoolean();
        assertFalse(VanishEnableAuthorityFence.commitIfEligible(StaffRank.MOD, () -> null,
                () -> written.set(true), () -> compensated.set(true)));
        assertFalse(written.get());
        assertFalse(compensated.get());
    }
    @Test void changedRankDoesNotCommitUsingStaleModePolicy() {
        assertFalse(VanishEnableAuthorityFence.eligible(StaffRank.ADMIN, StaffRank.HELPER));
        assertFalse(VanishEnableAuthorityFence.eligible(StaffRank.MOD, StaffRank.SYSTEM));
    }
    @Test void rankRevokedDuringWriteCompensatesBeforePublication() {
        AtomicReference<StaffRank> rank = new AtomicReference<>(StaffRank.MOD);
        AtomicBoolean durable = new AtomicBoolean();
        assertFalse(VanishEnableAuthorityFence.commitIfEligible(StaffRank.MOD, rank::get,
                () -> { durable.set(true); rank.set(null); }, () -> durable.set(false)));
        assertFalse(durable.get());
    }
    @Test void stableModRankCommitsWithoutCompensation() {
        AtomicBoolean durable = new AtomicBoolean();
        assertTrue(VanishEnableAuthorityFence.commitIfEligible(StaffRank.MOD, () -> StaffRank.MOD,
                () -> durable.set(true), () -> fail("unexpected compensation")));
        assertTrue(durable.get());
    }
    @Test void failedCompensationCannotReportSuccess() {
        AtomicReference<StaffRank> rank = new AtomicReference<>(StaffRank.MOD);
        assertThrows(IllegalStateException.class, () ->
                VanishEnableAuthorityFence.commitIfEligible(StaffRank.MOD, rank::get,
                        () -> rank.set(null), () -> { throw new IllegalStateException("unavailable"); }));
    }
}
