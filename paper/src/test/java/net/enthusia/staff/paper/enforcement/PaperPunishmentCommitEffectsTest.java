package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PaperPunishmentCommitEffectsTest {
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
