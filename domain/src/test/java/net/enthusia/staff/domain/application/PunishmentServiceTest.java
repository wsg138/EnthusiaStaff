package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

class PunishmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final UUID TARGET = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void developerCannotEvaluateOrPersistConfiguredPunishment() {
        CapturingStore store = new CapturingStore(List.of());
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v1", List.of(policy(StaffRank.MOD, standardSteps()))
        );
        PunishmentService service = service(policies, store);

        PunishmentEvaluation.Rejected rejected = assertInstanceOf(
                PunishmentEvaluation.Rejected.class,
                service.evaluate(request(StaffRank.DEVELOPER, List.of()), OperationalMode.ACTIVE)
        );

        assertEquals("FORBIDDEN", rejected.code());
        assertEquals(0, store.plans.size());
        assertEquals(0, store.historyReads);
    }

    @Test
    void configuredReasonRankUsesPunishmentAuthorityNotEnumOrder() {
        CapturingStore store = new CapturingStore(List.of());
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v1", List.of(policy(StaffRank.ADMIN, standardSteps()))
        );
        PunishmentService service = service(policies, store);

        PunishmentEvaluation.Rejected mod = assertInstanceOf(
                PunishmentEvaluation.Rejected.class,
                service.evaluate(request(StaffRank.MOD, List.of()), OperationalMode.ACTIVE)
        );
        assertEquals("RANK_REQUIRED", mod.code());
        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluate(request(StaffRank.ADMIN, List.of()), OperationalMode.ACTIVE)
        );
    }

    @Test
    void aliasEvaluationAndCommitUseTheCanonicalReasonIdentity() {
        ReasonPolicy canonical = policy(StaffRank.MOD, standardSteps());
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v2",
                List.of(canonical),
                Map.of("chat.old-toxicity", canonical.id()),
                List.of()
        );
        CapturingStore store = new CapturingStore(List.of());
        PunishmentService service = service(policies, store);
        CreatePunishmentRequest aliasRequest = request(StaffRank.MOD, List.of(), "chat.old-toxicity");
        PunishmentAssessment assessment = assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluate(aliasRequest, OperationalMode.ACTIVE)
        ).assessment();

        assertInstanceOf(
                PunishmentResult.Accepted.class,
                service.createConfirmed(
                        aliasRequest,
                        OperationalMode.ACTIVE,
                        PunishmentExpectation.from(assessment)
                )
        );

        PunishmentPlan committed = store.plans.getFirst();
        assertEquals(canonical.id(), committed.reasonId());
        assertEquals(canonical.family(), committed.family());
        assertEquals(canonical.publicReason(), committed.publicReason());
        assertEquals("v2", committed.configurationVersion());
    }

    @Test
    void modMayLowerButCannotRaiseConfiguredStep() {
        List<PunishmentStep> steps = standardSteps();
        CapturingStore recentHistory = new CapturingStore(List.of(
                new PriorOffense("chat", 10, 0, NOW.minus(Duration.ofDays(10)), true, false)
        ));
        PunishmentService lowerService = service(
                new AtomicReasonPolicyRepository("v1", List.of(policy(StaffRank.MOD, steps))),
                recentHistory
        );
        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                lowerService.evaluate(request(StaffRank.MOD, steps.get(1).sanctions()), OperationalMode.ACTIVE)
        );

        CapturingStore noHistory = new CapturingStore(List.of());
        PunishmentService raiseService = service(
                new AtomicReasonPolicyRepository("v1", List.of(policy(StaffRank.MOD, steps))),
                noHistory
        );
        PunishmentEvaluation.Rejected raised = assertInstanceOf(
                PunishmentEvaluation.Rejected.class,
                raiseService.evaluate(request(StaffRank.MOD, steps.get(2).sanctions()), OperationalMode.ACTIVE)
        );
        assertEquals("FORBIDDEN_OVERRIDE", raised.code());
        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                raiseService.evaluate(request(StaffRank.ADMIN, steps.get(2).sanctions()), OperationalMode.ACTIVE)
        );
    }

    @Test
    void adminMayCustomizeConfiguredTypesButNotCreateArbitraryCombination() {
        List<PunishmentStep> steps = standardSteps();
        PunishmentService service = service(
                new AtomicReasonPolicyRepository("v1", List.of(policy(StaffRank.MOD, steps))),
                new CapturingStore(List.of())
        );
        List<SanctionSpec> customDuration = List.of(
                new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(3)))
        );
        List<SanctionSpec> arbitrary = List.of(
                new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(3)))
        );

        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluate(request(StaffRank.ADMIN, customDuration), OperationalMode.ACTIVE)
        );
        assertInstanceOf(
                PunishmentEvaluation.Rejected.class,
                service.evaluate(request(StaffRank.ADMIN, arbitrary), OperationalMode.ACTIVE)
        );
        assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluate(request(StaffRank.FOUNDER, arbitrary), OperationalMode.ACTIVE)
        );
    }

    @Test
    void policyReplacementCannotChangeCommittedPlanOrConfirmStaleReview() {
        List<PunishmentStep> originalSteps = List.of(new PunishmentStep(0, "Mute", List.of(
                new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(1)))
        )));
        List<PunishmentStep> editedSteps = List.of(new PunishmentStep(0, "Mute", List.of(
                new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(30)))
        )));
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v1", List.of(policy(StaffRank.MOD, originalSteps))
        );
        CapturingStore store = new CapturingStore(List.of());
        PunishmentService service = service(policies, store);
        CreatePunishmentRequest initial = request(StaffRank.MOD, List.of());
        PunishmentAssessment reviewed = assertInstanceOf(
                PunishmentEvaluation.Allowed.class,
                service.evaluate(initial, OperationalMode.ACTIVE)
        ).assessment();

        assertInstanceOf(
                PunishmentResult.Accepted.class,
                service.createConfirmed(initial, OperationalMode.ACTIVE, PunishmentExpectation.from(reviewed))
        );
        PunishmentPlan committed = store.plans.getFirst();

        policies.replace("v2", List.of(policy(StaffRank.MOD, editedSteps)));
        PunishmentResult.Rejected stale = assertInstanceOf(
                PunishmentResult.Rejected.class,
                service.createConfirmed(
                        request(StaffRank.MOD, List.of()),
                        OperationalMode.ACTIVE,
                        PunishmentExpectation.from(reviewed)
                )
        );

        assertEquals("RECOMMENDATION_CHANGED", stale.code());
        assertEquals(1, store.plans.size());
        assertEquals("v1", committed.configurationVersion());
        assertEquals(originalSteps.getFirst().sanctions(), committed.sanctions());
    }

    private static PunishmentService service(
            AtomicReasonPolicyRepository policies,
            ModerationStore store
    ) {
        return new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom(new byte[]{1, 2, 3, 4})),
                new DefaultAuthorizationPolicy(),
                policies,
                store,
                new EscalationEngine()
        );
    }

    private static CreatePunishmentRequest request(StaffRank rank, List<SanctionSpec> override) {
        return request(rank, override, "chat.toxicity");
    }

    private static CreatePunishmentRequest request(
            StaffRank rank,
            List<SanctionSpec> override,
            String reasonId
    ) {
        return new CreatePunishmentRequest(
                new IdempotencyKey("test:" + UUID.randomUUID()),
                TARGET,
                new Actor(UUID.randomUUID(), rank.name(), rank),
                reasonId,
                "Test explanation",
                CaseVisibility.PUBLIC,
                override
        );
    }

    private static ReasonPolicy policy(StaffRank requiredRank, List<PunishmentStep> steps) {
        return new ReasonPolicy(
                "chat.toxicity",
                "chat",
                "Chat toxicity",
                10,
                true,
                steps,
                List.of(),
                true,
                true,
                false,
                requiredRank,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }

    private static List<PunishmentStep> standardSteps() {
        return List.of(
                new PunishmentStep(0, "Warning", List.of(
                        new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())
                )),
                new PunishmentStep(1, "One day mute", List.of(
                        new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(1)))
                )),
                new PunishmentStep(2, "Seven day mute", List.of(
                        new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(7)))
                ))
        );
    }

    private static final class CapturingStore implements ModerationStore {
        private final List<PriorOffense> history;
        private final List<PunishmentPlan> plans = new ArrayList<>();
        private int historyReads;

        private CapturingStore(List<PriorOffense> history) {
            this.history = List.copyOf(history);
        }

        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            historyReads++;
            return history;
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            plans.add(plan);
            return new PunishmentResult.Accepted(plan.caseId(), false);
        }
    }
}
