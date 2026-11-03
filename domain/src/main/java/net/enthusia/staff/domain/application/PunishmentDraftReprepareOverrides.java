package net.enthusia.staff.domain.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/**
 * Preserves a saved draft's intended outcome when editing notes/visibility,
 * even if a resumed draft has no in-memory PunishmentAssessment.
 *
 * Every returned override is re-evaluated against current policy and
 * authorization by PunishmentDraftWorkflow.prepare; it grants no authority.
 */
public final class PunishmentDraftReprepareOverrides {
    private PunishmentDraftReprepareOverrides() { }

    public static List<SanctionSpec> choose(
            PunishmentExpectation expected,
            Optional<PunishmentAssessment> assessment,
            Optional<ReasonPolicy> policy
    ) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(assessment);
        Objects.requireNonNull(policy);
        if (assessment.isPresent()) {
            PunishmentAssessment current = assessment.orElseThrow();
            return current.sanctions().equals(current.escalation().selectedStep().sanctions())
                    ? List.of() : current.sanctions();
        }
        if (expected.customDuration()) {
            return expected.sanctions();
        }
        if (policy.isEmpty()) {
            // Fails closed in prepare; do not silently downgrade the saved draft.
            return expected.sanctions();
        }
        ReasonPolicy active = policy.orElseThrow();
        if (expected.stepOrdinal() >= active.steps().size()) {
            return expected.sanctions();
        }
        return expected.sanctions().equals(active.steps().get(expected.stepOrdinal()).sanctions())
                ? List.of() : expected.sanctions();
    }
}
