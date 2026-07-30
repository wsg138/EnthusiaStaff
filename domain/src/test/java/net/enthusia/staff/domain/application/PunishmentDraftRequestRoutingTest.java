package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
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
import net.enthusia.staff.domain.ports.PunishmentDraftStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentDraftRequestRoutingTest {
    private static final Instant NOW = Instant.parse("2026-07-30T18:00:00Z");
    private static final UUID DRAFT_ID = UUID.fromString("53000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("53000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("53000000-0000-0000-0000-000000000003");
    private static final UUID TARGET_ID = UUID.fromString("53000000-0000-0000-0000-000000000004");

    @Test
    void developerConfirmationCreatesDurableRequestInsteadOfPunishment() {
        Fixture fixture = fixture(SanctionLength.temporary(Duration.ofDays(1)));
        prepare(fixture, StaffRank.DEVELOPER);

        PunishmentDraftConfirmation.Requested requested = assertInstanceOf(
                PunishmentDraftConfirmation.Requested.class,
                fixture.workflow().confirmRouted(DRAFT_ID, actor(StaffRank.DEVELOPER), OperationalMode.ACTIVE)
        );

        assertEquals(REQUEST_ID, requested.submitted().request().requestId());
        assertEquals(StaffRank.DEVELOPER, requested.submitted().request().proposal().requester().rank());
        assertEquals(PunishmentRequestStatus.PENDING, requested.submitted().request().status());
        assertTrue(fixture.moderation().plans.isEmpty());
        assertFalse(fixture.workflow().find(DRAFT_ID, ACTOR_ID).isPresent());
    }

    @Test
    void helperPermanentConfirmationCreatesDurableRequest() {
        Fixture fixture = fixture(SanctionLength.permanent());
        prepare(fixture, StaffRank.HELPER);

        PunishmentDraftConfirmation.Requested requested = assertInstanceOf(
                PunishmentDraftConfirmation.Requested.class,
                fixture.workflow().confirmRouted(DRAFT_ID, actor(StaffRank.HELPER), OperationalMode.ACTIVE)
        );

        assertEquals(StaffRank.HELPER, requested.submitted().request().proposal().requester().rank());
        assertTrue(requested.submitted().request().proposal().sanctions().stream()
                .anyMatch(specification -> specification.length().isPermanent()));
        assertTrue(fixture.moderation().plans.isEmpty());
    }

    @Test
    void helperTemporaryConfirmationStillAppliesDirectly() {
        Fixture fixture = fixture(SanctionLength.temporary(Duration.ofHours(6)));
        prepare(fixture, StaffRank.HELPER);

        PunishmentDraftConfirmation.Applied applied = assertInstanceOf(
                PunishmentDraftConfirmation.Applied.class,
                fixture.workflow().confirmRouted(DRAFT_ID, actor(StaffRank.HELPER), OperationalMode.ACTIVE)
        );

        assertEquals(new CaseId("TESTCASE00000002"), applied.accepted().caseId());
        assertEquals(1, fixture.moderation().plans.size());
        assertTrue(fixture.requests().entries.isEmpty());
    }

    @Test
    void changedRecommendationRetainsDraftAndCreatesNoRequest() {
        Fixture fixture = fixture(SanctionLength.temporary(Duration.ofDays(1)));
        prepare(fixture, StaffRank.DEVELOPER);
        fixture.policies().replace(
                "v2",
                List.of(policy(SanctionLength.temporary(Duration.ofDays(7))))
        );

        PunishmentDraftConfirmation.Rejected rejected = assertInstanceOf(
                PunishmentDraftConfirmation.Rejected.class,
                fixture.workflow().confirmRouted(DRAFT_ID, actor(StaffRank.DEVELOPER), OperationalMode.ACTIVE)
        );

        assertEquals("RECOMMENDATION_CHANGED", rejected.code());
        assertTrue(fixture.workflow().find(DRAFT_ID, ACTOR_ID).isPresent());
        assertTrue(fixture.requests().entries.isEmpty());
        assertTrue(fixture.moderation().plans.isEmpty());
    }

    @Test
    void submittedRequestSurvivesDraftCleanupFailureAndRetryIsIdempotent() {
        Fixture fixture = fixture(SanctionLength.temporary(Duration.ofDays(1)));
        prepare(fixture, StaffRank.DEVELOPER);
        fixture.drafts().failDelete = true;

        PunishmentRequestDraftCleanupException failure = assertThrows(
                PunishmentRequestDraftCleanupException.class,
                () -> fixture.workflow().confirmRouted(
                        DRAFT_ID,
                        actor(StaffRank.DEVELOPER),
                        OperationalMode.ACTIVE
                )
        );

        assertEquals(REQUEST_ID, failure.submitted().request().requestId());
        assertEquals(1, fixture.requests().entries.size());
        assertTrue(fixture.workflow().find(DRAFT_ID, ACTOR_ID).isPresent());

        fixture.drafts().failDelete = false;
        PunishmentDraftConfirmation.Requested replay = assertInstanceOf(
                PunishmentDraftConfirmation.Requested.class,
                fixture.workflow().confirmRouted(DRAFT_ID, actor(StaffRank.DEVELOPER), OperationalMode.ACTIVE)
        );
        assertTrue(replay.submitted().replayed());
        assertEquals(1, fixture.requests().entries.size());
        assertFalse(fixture.workflow().find(DRAFT_ID, ACTOR_ID).isPresent());
    }

    private static PunishmentDraftEvaluation.Prepared prepare(Fixture fixture, StaffRank rank) {
        return assertInstanceOf(
                PunishmentDraftEvaluation.Prepared.class,
                fixture.workflow().prepare(
                        new PreparePunishmentDraftRequest(
                                TARGET_ID,
                                actor(rank),
                                "chat.request-routing",
                                "Reviewed for routed punishment confirmation",
                                CaseVisibility.PUBLIC,
                                "punish"
                        ),
                        OperationalMode.ACTIVE
                )
        );
    }

    private static Fixture fixture(SanctionLength length) {
        AtomicReasonPolicyRepository policies = new AtomicReasonPolicyRepository(
                "v1",
                List.of(policy(length))
        );
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        CapturingModerationStore moderation = new CapturingModerationStore();
        InMemoryDraftStore drafts = new InMemoryDraftStore();
        InMemoryRequestStore requests = new InMemoryRequestStore();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SecureIdentifiers identifiers = new SecureIdentifiers(new SecureRandom());
        PunishmentService punishments = new PunishmentService(
                clock,
                identifiers,
                authorization,
                policies,
                moderation,
                new EscalationEngine()
        );
        PunishmentRequestService requestService = new PunishmentRequestService(
                clock,
                Duration.ofDays(7),
                Duration.ofMinutes(2),
                () -> REQUEST_ID,
                identifiers,
                authorization,
                punishments,
                requests
        );
        PunishmentDraftWorkflow workflow = new PunishmentDraftWorkflow(
                clock,
                Duration.ofHours(24),
                () -> DRAFT_ID,
                punishments,
                requestService,
                drafts
        );
        return new Fixture(workflow, policies, drafts, requests, moderation);
    }

    private static ReasonPolicy policy(SanctionLength length) {
        return new ReasonPolicy(
                "chat.request-routing",
                "chat",
                "Request routing",
                10,
                true,
                List.of(new PunishmentStep(0, "Configured", List.of(
                        new SanctionSpec(SanctionType.MUTE, length)
                ))),
                List.of(),
                true,
                true,
                false,
                StaffRank.MOD,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(ACTOR_ID, rank.name(), rank);
    }

    private record Fixture(
            PunishmentDraftWorkflow workflow,
            AtomicReasonPolicyRepository policies,
            InMemoryDraftStore drafts,
            InMemoryRequestStore requests,
            CapturingModerationStore moderation
    ) {
    }

    private static final class CapturingModerationStore implements ModerationStore {
        private final List<PunishmentPlan> plans = new ArrayList<>();

        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            return List.of();
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            plans.add(plan);
            return new PunishmentResult.Accepted(new CaseId("TESTCASE00000002"), false);
        }
    }

    private static final class InMemoryDraftStore implements PunishmentDraftStore {
        private final Map<UUID, PunishmentDraft> entries = new HashMap<>();
        private boolean failDelete;

        @Override
        public void save(PunishmentDraft draft) {
            entries.put(draft.draftId(), draft);
        }

        @Override
        public Optional<PunishmentDraft> find(UUID draftId, UUID actorId, Instant now) {
            return Optional.ofNullable(entries.get(draftId))
                    .filter(draft -> draft.actorId().equals(actorId) && !draft.expiredAt(now));
        }

        @Override
        public Optional<PunishmentDraft> findLatest(UUID actorId, UUID targetId, Instant now) {
            return entries.values().stream()
                    .filter(draft -> draft.actorId().equals(actorId)
                            && draft.targetId().equals(targetId)
                            && !draft.expiredAt(now))
                    .findFirst();
        }

        @Override
        public boolean delete(UUID draftId, UUID actorId) {
            if (failDelete) {
                throw new IllegalStateException("simulated cleanup failure");
            }
            PunishmentDraft draft = entries.get(draftId);
            return draft != null && draft.actorId().equals(actorId) && entries.remove(draftId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            int before = entries.size();
            entries.values().removeIf(draft -> draft.expiredAt(now));
            return before - entries.size();
        }
    }

    private static final class InMemoryRequestStore implements PunishmentRequestStore {
        private final Map<UUID, PunishmentApprovalRequest> entries = new LinkedHashMap<>();

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            PunishmentApprovalRequest existing = entries.values().stream()
                    .filter(value -> value.submissionKey().equals(request.submissionKey()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return new PunishmentRequestResult.Submitted(existing, true);
            }
            entries.put(request.requestId(), request);
            return new PunishmentRequestResult.Submitted(request, false);
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return Optional.ofNullable(entries.get(requestId));
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            return entries.values().stream().filter(request -> request.pendingAt(now)).limit(limit).toList();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            throw new UnsupportedOperationException("not needed by routing tests");
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            throw new UnsupportedOperationException("not needed by routing tests");
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            throw new UnsupportedOperationException("not needed by routing tests");
        }

        @Override
        public int expire(Instant now) {
            return 0;
        }
    }
}
