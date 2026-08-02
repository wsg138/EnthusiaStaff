package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class PunishmentRequestReviewVisibilityTest {
    private static final Instant NOW = Instant.parse("2026-07-30T20:00:00Z");
    private static final Actor MOD = actor("55000000-0000-0000-0000-000000000001", StaffRank.MOD);
    private static final Actor ADMIN = actor("55000000-0000-0000-0000-000000000002", StaffRank.ADMIN);
    private static final Actor DEVELOPER = actor("55000000-0000-0000-0000-000000000003", StaffRank.DEVELOPER);
    private static final Actor HELPER = actor("55000000-0000-0000-0000-000000000004", StaffRank.HELPER);

    @Test
    void reviewableQueueFiltersSelfUnauthorizedAndRankInsufficientRequests() {
        InMemoryStore store = new InMemoryStore();
        PunishmentApprovalRequest modRequest = pending(1, HELPER, StaffRank.MOD, NOW.plus(Duration.ofDays(1)));
        PunishmentApprovalRequest adminRequest = pending(2, DEVELOPER, StaffRank.ADMIN, NOW.plus(Duration.ofDays(1)));
        Actor requesterBeforePromotion = new Actor(MOD.id(), "Former helper", StaffRank.HELPER);
        PunishmentApprovalRequest selfRequest = pending(
                3,
                requesterBeforePromotion,
                StaffRank.MOD,
                NOW.plus(Duration.ofDays(1))
        );
        store.put(modRequest);
        store.put(adminRequest);
        store.put(selfRequest);
        PunishmentRequestService service = service(store);

        assertEquals(List.of(modRequest), service.reviewable(MOD, 500));
        assertEquals(List.of(modRequest, adminRequest, selfRequest), service.reviewable(ADMIN, 500));
        assertTrue(service.reviewable(DEVELOPER, 500).isEmpty());
    }

    @Test
    void requestLookupRemainsReadOnlyUntilBoundedExpirationRuns() {
        InMemoryStore store = new InMemoryStore();
        PunishmentApprovalRequest expired = pending(
                4,
                HELPER,
                StaffRank.MOD,
                NOW.minus(Duration.ofMinutes(1))
        );
        store.put(expired);
        PunishmentRequestService service = service(store);

        PunishmentApprovalRequest presented = service.find(expired.requestId()).orElseThrow();
        assertEquals(PunishmentRequestStatus.PENDING, presented.status());
        assertEquals(0, presented.revision());

        assertEquals(1, service.expire());
        PunishmentApprovalRequest transitioned = service.find(expired.requestId()).orElseThrow();
        assertEquals(PunishmentRequestStatus.EXPIRED, transitioned.status());
        assertEquals(1, transitioned.revision());
        assertEquals("Punishment request expired without a decision", transitioned.resolutionNote());
    }

    @Test
    void perRequestReviewPolicyRejectsDeveloperSelfApprovalAndInsufficientRank() {
        PunishmentRequestService service = service(new InMemoryStore());
        PunishmentApprovalRequest modRequest = pending(5, HELPER, StaffRank.MOD, NOW.plus(Duration.ofDays(1)));
        PunishmentApprovalRequest adminRequest = pending(6, DEVELOPER, StaffRank.ADMIN, NOW.plus(Duration.ofDays(1)));
        Actor promotedRequester = new Actor(HELPER.id(), "Promoted requester", StaffRank.MOD);

        assertFalse(service.mayReview(DEVELOPER, modRequest));
        assertFalse(service.mayReview(promotedRequester, modRequest));
        assertFalse(service.mayReview(MOD, adminRequest));
        assertTrue(service.mayReview(ADMIN, adminRequest));
    }

    private static PunishmentRequestService service(InMemoryStore store) {
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        SanctionSpec sanction = permanentBan();
        ReasonPolicy policy = new ReasonPolicy(
                "review.visibility",
                "review",
                "Review visibility",
                10,
                true,
                List.of(new PunishmentStep(0, "Permanent ban", List.of(sanction))),
                List.of(),
                true,
                true,
                false,
                StaffRank.MOD,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
        PunishmentService punishments = new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                new AtomicReasonPolicyRepository("v1", List.of(policy)),
                new NoOpModerationStore(),
                new EscalationEngine()
        );
        return new PunishmentRequestService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7),
                Duration.ofMinutes(2),
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                punishments,
                store
        );
    }

    private static PunishmentApprovalRequest pending(
            int sequence,
            Actor requester,
            StaffRank requiredRank,
            Instant expiresAt
    ) {
        SanctionSpec sanction = permanentBan();
        PunishmentStep step = new PunishmentStep(0, "Permanent ban", List.of(sanction));
        Instant createdAt = expiresAt.isAfter(NOW) ? NOW.plusSeconds(sequence) : NOW.minus(Duration.ofHours(2));
        PunishmentProposal proposal = new PunishmentProposal(
                new UUID(0x5500000000000000L, 100L + sequence),
                requester,
                "review.visibility." + sequence,
                "review",
                "Review visibility " + sequence,
                "Evidence-backed request " + sequence,
                "v1",
                CaseVisibility.PUBLIC,
                requiredRank,
                new EscalationDecision(0, 0, 0, List.of(), step),
                List.of(sanction)
        );
        return PunishmentApprovalRequest.pending(
                new UUID(0x5500000000000000L, sequence),
                new IdempotencyKey("review-visibility:" + sequence),
                proposal,
                createdAt,
                expiresAt
        );
    }

    private static SanctionSpec permanentBan() {
        return new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent());
    }

    private static Actor actor(String id, StaffRank rank) {
        return new Actor(UUID.fromString(id), rank.name(), rank);
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

    private static final class InMemoryStore implements PunishmentRequestStore {
        private final Map<UUID, PunishmentApprovalRequest> requests = new LinkedHashMap<>();

        void put(PunishmentApprovalRequest request) {
            requests.put(request.requestId(), request);
        }

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            put(request);
            return new PunishmentRequestResult.Submitted(request, false);
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return Optional.ofNullable(requests.get(requestId));
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            return requests.values().stream()
                    .filter(request -> request.pendingAt(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            throw new UnsupportedOperationException("not required by visibility tests");
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            throw new UnsupportedOperationException("not required by visibility tests");
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            throw new UnsupportedOperationException("not required by visibility tests");
        }

        @Override
        public int expire(Instant now) {
            List<PunishmentApprovalRequest> expired = new ArrayList<>();
            requests.values().stream()
                    .filter(request -> request.status() == PunishmentRequestStatus.PENDING)
                    .filter(request -> !request.expiresAt().isAfter(now))
                    .forEach(expired::add);
            expired.forEach(request -> requests.put(
                    request.requestId(),
                    new PunishmentApprovalRequest(
                            request.requestId(),
                            request.submissionKey(),
                            request.proposal(),
                            request.createdAt(),
                            request.expiresAt(),
                            PunishmentRequestStatus.EXPIRED,
                            request.revision() + 1,
                            null,
                            "Punishment request expired without a decision",
                            null,
                            now
                    )
            ));
            return expired.size();
        }
    }
}
