package net.enthusia.staff.domain.escalation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class ReasonPolicyLadderEditTest {
    @Test
    void currentPolicyInterpretsStoredOrdinalWithoutReusingOldStepContent() {
        ReasonPolicy oldPolicy = policy(
                step(0, "Old warning", SanctionLength.instant(), SanctionType.WARNING),
                step(1, "Old mute", SanctionLength.temporary(Duration.ofDays(1)), SanctionType.MUTE)
        );
        ReasonPolicy currentPolicy = policy(
                step(0, "New warning", SanctionLength.instant(), SanctionType.WARNING),
                step(1, "New mute", SanctionLength.temporary(Duration.ofDays(7)), SanctionType.MUTE),
                step(2, "New ban", SanctionLength.temporary(Duration.ofDays(3)), SanctionType.NETWORK_BAN)
        );

        int storedOrdinal = oldPolicy.stepAt(1).ordinal();

        assertEquals("New mute", currentPolicy.stepAt(storedOrdinal).label());
        assertEquals(
                SanctionLength.temporary(Duration.ofDays(7)),
                currentPolicy.stepAt(storedOrdinal).sanctions().getFirst().length()
        );
    }

    @Test
    void ordinalBeyondEditedFiniteLadderUsesCurrentFinalStep() {
        ReasonPolicy shortenedPolicy = policy(
                step(0, "Current warning", SanctionLength.instant(), SanctionType.WARNING),
                step(1, "Current final ban", SanctionLength.permanent(), SanctionType.NETWORK_BAN)
        );

        assertEquals("Current final ban", shortenedPolicy.stepAt(8).label());
        assertEquals(1, shortenedPolicy.stepAt(8).ordinal());
    }

    private static ReasonPolicy policy(PunishmentStep... steps) {
        return new ReasonPolicy(
                "test.ladder-edit",
                "test",
                "Ladder edit test",
                20,
                false,
                List.of(steps)
        );
    }

    private static PunishmentStep step(
            int ordinal,
            String label,
            SanctionLength length,
            SanctionType type
    ) {
        return new PunishmentStep(ordinal, label, List.of(new SanctionSpec(type, length)));
    }
}
