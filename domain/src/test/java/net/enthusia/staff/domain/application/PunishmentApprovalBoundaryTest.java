package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
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
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
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

class PunishmentApprovalBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");

    @Test
    void helperMayApplyConfiguredTemporaryPunishment() {
        PunishmentService service = service(SanctionLength.temporary(Duration.ofDays(7)));

        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluate(request(StaffRank.HELPER), OperationalMode.ACTIVE)
        );
    }

    @Test
    void helperPermanentPunishmentRequiresApprovalButCanBeRequested() {
        PunishmentService service = service(SanctionLength.permanent());

        PunishmentEvaluation.Rejected rejected = assertInstanceOf(
                PunishmentEvaluation.Rejected.class,
                service.evaluate(request(StaffRank.HELPER), OperationalMode.ACTIVE)
        );
        assertEquals("APPROVAL_REQUIRED", rejected.code());
        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluateForRequest(request(StaffRank.HELPER), OperationalMode.ACTIVE)
        );
    }

    @Test
    void developerCanRequestButCannotDirectlyApplyAnyPunishment() {
        PunishmentService service = service(SanctionLength.temporary(Duration.ofHours(6)));

        PunishmentEvaluation.Rejected rejected = assertInstanceOf(
                PunishmentEvaluation.Rejected.class,
                service.evaluate(request(StaffRank.DEVELOPER), OperationalMode.ACTIVE)
        );
        assertEquals("FORBIDDEN", rejected.code());
        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluateForRequest(request(StaffRank.DEVELOPER), OperationalMode.ACTIVE)
        );
    }

    private static PunishmentService service(SanctionLength length) {
        PunishmentStep step = new PunishmentStep(0, "Configured", List.of(
                new SanctionSpec(SanctionType.NETWORK_BAN, length)
        ));
        ReasonPolicy policy = new ReasonPolicy(
                "test.reason",
                "test",
                "Test reason",
                10,
                true,
                List.of(step),
                List.of(),
                true,
                true,
                false,
                StaffRank.MOD,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
        return new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom(new byte[]{1, 2, 3, 4})),
                new DefaultAuthorizationPolicy(),
                new AtomicReasonPolicyRepository("v1", List.of(policy)),
                new NoOpModerationStore(),
                new EscalationEngine()
        );
    }

    private static CreatePunishmentRequest request(StaffRank rank) {
        return new CreatePunishmentRequest(
                new IdempotencyKey("approval-boundary:" + UUID.randomUUID()),
                UUID.randomUUID(),
                new Actor(UUID.randomUUID(), rank.name(), rank),
                "test.reason",
                "Test approval boundary",
                CaseVisibility.PUBLIC,
                List.of()
        );
    }

    private static final class NoOpModerationStore implements ModerationStore {
        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            return List.of();
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            return new PunishmentResult.Accepted(plan.caseId(), false);
        }
    }
}
