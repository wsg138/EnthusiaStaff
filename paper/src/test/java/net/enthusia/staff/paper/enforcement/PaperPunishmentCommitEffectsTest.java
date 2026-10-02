package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PaperPunishmentCommitEffectsTest {
    @Test
    void localCommitAndNetworkDeliveryShareOneOnlineEffectClaim() {
        var claims = new PaperPunishmentCommitEffects.DeliveryClaims();
        var now = java.time.Instant.parse("2026-10-01T03:00:00Z");
        var caseId = new net.enthusia.staff.common.CaseId("TESTCASE00000001");
        assertTrue(claims.claim(caseId, now));
        assertFalse(claims.claim(caseId, now.plusSeconds(1)));
        assertFalse(claims.claim(caseId, now.plusSeconds(120)));
        assertTrue(claims.claim(caseId, now.plusSeconds(301)));
    }
    @Test
    void delayedAndFutureNetworkEventsCannotReplayOnlineEffects() {
        var now = java.time.Instant.parse("2026-10-01T03:00:00Z");
        assertTrue(PaperPunishmentCommitEffects.recent(now, now));
        assertTrue(PaperPunishmentCommitEffects.recent(now.minusSeconds(120), now));
        assertFalse(PaperPunishmentCommitEffects.recent(now.minusSeconds(121), now));
        assertFalse(PaperPunishmentCommitEffects.recent(now.plusSeconds(31), now));
    }
    @Test
    void banTakesPrecedenceOverKickAndWarning() {
        assertEquals(
                PaperPunishmentCommitEffects.CommitEffect.BAN,
                PaperPunishmentCommitEffects.effectFor(List.of(
                        instant(SanctionType.WARNING),
                        instant(SanctionType.KICK),
                        new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(21)))
                ))
        );
    }

    @Test
    void kickTakesPrecedenceOverWarning() {
        assertEquals(
                PaperPunishmentCommitEffects.CommitEffect.KICK,
                PaperPunishmentCommitEffects.effectFor(List.of(
                        instant(SanctionType.WARNING),
                        instant(SanctionType.KICK)
                ))
        );
    }

    @Test
    void warningAndNoOnlineEffectAreDistinguished() {
        assertEquals(
                PaperPunishmentCommitEffects.CommitEffect.WARNING,
                PaperPunishmentCommitEffects.effectFor(List.of(instant(SanctionType.WARNING)))
        );
        assertEquals(
                PaperPunishmentCommitEffects.CommitEffect.NONE,
                PaperPunishmentCommitEffects.effectFor(List.of(
                        new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofHours(1)))
                ))
        );
    }

    private static SanctionSpec instant(SanctionType type) {
        return new SanctionSpec(type, SanctionLength.instant());
    }
}
