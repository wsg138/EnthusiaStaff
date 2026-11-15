package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentDraftReprepareOverridesTest {
    private static final SanctionSpec WARN =
            new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
    private static final SanctionSpec MUTE =
            new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofMinutes(30)));
    private static final SanctionSpec CUSTOM =
            new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofMinutes(47)));

    private static ReasonPolicy policy() {
        return new ReasonPolicy("spam.low-level", "spam", "Spam", 10, true, List.of(
                new PunishmentStep(0, "warning", List.of(WARN)),
                new PunishmentStep(1, "mute", List.of(MUTE))
        ));
    }

    @Test
    void resumedCustomDurationIsPreservedDuringNotesAndVisibilityEdits() {
        var expected = new PunishmentExpectation("v1", 1, "mute", List.of(CUSTOM), true);
        assertEquals(List.of(CUSTOM), PunishmentDraftReprepareOverrides.choose(
                expected, Optional.empty(), Optional.of(policy())));
    }

    @Test
    void resumedConfiguredStepOverrideRemainsExplicit() {
        var expected = new PunishmentExpectation("v1", 0, "warning", List.of(MUTE), false);
        assertEquals(List.of(MUTE), PunishmentDraftReprepareOverrides.choose(
                expected, Optional.empty(), Optional.of(policy())));
    }

    @Test
    void ordinaryResumedDraftKeepsDefaultPathAndMissingPolicyCannotSilentlyReplaceIt() {
        var expected = new PunishmentExpectation("v1", 0, "warning", List.of(WARN), false);
        assertTrue(PunishmentDraftReprepareOverrides.choose(
                expected, Optional.empty(), Optional.of(policy())).isEmpty());
        assertEquals(List.of(WARN), PunishmentDraftReprepareOverrides.choose(
                expected, Optional.empty(), Optional.empty()));
    }
}
