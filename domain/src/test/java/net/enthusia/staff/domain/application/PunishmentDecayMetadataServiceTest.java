package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentDecayMetadataServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T05:00:00Z");
    private static final UUID TARGET = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final Actor MOD = new Actor(
            UUID.fromString("54000000-0000-0000-0000-000000000002"),
            "DecayMod",
            StaffRank.MOD
    );

    @Test
    void committedPlanCapturesEligiblePolicyMetadata() {
        assertEquals(
                DecayEligibility.ELIGIBLE,
                committedPlan(true).escalation().resultingOffenseDecayEligibility()
        );
    }

    @Test
    void committedPlanCapturesIneligiblePolicyMetadata() {
        assertEquals(
                DecayEligibility.INELIGIBLE,
                committedPlan(false).escalation().resultingOffenseDecayEligibility()
        );
    }

    private static PunishmentPlan committedPlan(boolean decayEnabled) {
        CapturingStore store = new CapturingStore();
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "decay-policy-v1",
                List.of(policy(decayEnabled))
        );
        PunishmentService service = new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom(new byte[]{5, 4, 3, 2, 1})),
                new DefaultAuthorizationPolicy(),
                policies,
                store,
                new EscalationEngine()
        );
        CreatePunishmentRequest request = new CreatePunishmentRequest(
                new IdempotencyKey("decay-service:" + decayEnabled),
                TARGET,
                MOD,
                "test.decay-service",
                "Verify immutable decay metadata",
                CaseVisibility.PUBLIC,
                List.of()
        );

        assertInstanceOf(
                PunishmentResult.Accepted.class,
                service.create(request, OperationalMode.ACTIVE)
        );
        return store.plan;
    }

    private static ReasonPolicy policy(boolean decayEnabled) {
        PunishmentStep step = new PunishmentStep(
                0,
                "Warning",
                List.of(new SanctionSpec(
                        SanctionType.WARNING,
                        SanctionLength.instant()
                ))
        );
        return new ReasonPolicy(
                "test.decay-service",
                "test",
                "Decay service test",
                10,
                decayEnabled,
                List.of(step)
        );
    }

    private static final class CapturingStore implements ModerationStore {
        private PunishmentPlan plan;

        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            return List.of();
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            this.plan = plan;
            return new PunishmentResult.Accepted(plan.caseId(), false);
        }
    }
}
