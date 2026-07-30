package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T13:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("52000000-0000-0000-0000-000000000002");
    private static final Actor HELPER = actor("52000000-0000-0000-0000-000000000003", StaffRank.HELPER);
    private static final Actor DEVELOPER = actor("52000000-0000-0000-0000-000000000004", StaffRank.DEVELOPER);
    private static final Actor MOD = actor("52000000-0000-0000-0000-000000000005", StaffRank.MOD);
    private static final Actor ADMIN = actor("52000000-0000-0000-0000-000000000006", StaffRank.ADMIN);

    @Test
    void helperPermanentOutcomeBecomesDurablePendingRequest() {
        Fixture fixture = fixture(StaffRank.MOD, SanctionLength.permanent());

        PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                PunishmentRequestResult.Submitted.class,
                fixture.service().submit(request(HELPER), OperationalMode.ACTIVE)
        );

        assertEquals(PunishmentRequestStatus.PENDING, submitted.request().status());
        assertEquals(HELPER, submitted.request().proposal().requester());
        assertEquals(NOW.plus(Duration.ofDays(7)), submitted.request().expiresAt());
    }

    @Test
    void developerTemporaryProposalAlsoRequiresApproval() {
        Fixture fixture = fixture(
                StaffRank.MOD,
                SanctionLength.temporary(Duration.ofHours(6))
        );

        assertInstanceOf(
                PunishmentRequestResult.Submitted.class,
                fixture.service().submit(request(DEVELOPER), OperationalMode.ACTIVE)
        );
    }

    @Test
    void helperTemporaryOutcomeUsesDirectPunishmentFlow() {
        Fixture fixture = fixture(
                StaffRank.MOD,
                SanctionLength.temporary(Duration.ofHours(6))
        );

        PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                PunishmentRequestResult.Rejected.class,
                fixture.service().submit(request(HELPER), OperationalMode.ACTIVE)
        );

        assertEquals("APPROVAL_NOT_REQUIRED", rejected.code());
    }

    @Test
    void developerCannotAcquireAnApprovalLease() {
        Fixture fixture = fixture(StaffRank.MOD, SanctionLength.permanent());
        PunishmentRequestResult.Submitted submitted = submit(fixture, HELPER);

        PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                PunishmentRequestResult.Rejected.class,
                fixture.service().acquire(submitted.request().requestId(), DEVELOPER)
        );

        assertEquals("FORBIDDEN", rejected.code());
    }

    @Test
    void requesterCannotDecideTheirOwnRequest() {
        Fixture fixture = fixture(StaffRank.MOD, SanctionLength.permanent());
        PunishmentRequestResult.Submitted submitted = submit(fixture, HELPER);

        PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                PunishmentRequestResult.Rejected.class,
                fixture.service().acquire(submitted.request().requestId(), HELPER)
        );

        assertEquals("FORBIDDEN", rejected.code());
    }

    @Test
    void adminReasonCannotBeApprovedByMod() {
        Fixture fixture = fixture(StaffRank.ADMIN, SanctionLength.permanent());
        PunishmentRequestResult.Submitted submitted = submit(fixture, DEVELOPER);

        PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                PunishmentRequestResult.Rejected.class,
                fixture.service().acquire(submitted.request().requestId(), MOD)
        );

        assertEquals("APPROVER_RANK_REQUIRED", rejected.code());
        assertInstanceOf(
                PunishmentRequestResult.Leased.class,
                fixture.service().acquire(submitted.request().requestId(), ADMIN)
        );
    }

    @Test
    void leasedRequestCanBeApprovedOnce() {
        Fixture fixture = fixture(StaffRank.MOD, SanctionLength.permanent());
        PunishmentRequestResult.Submitted submitted = submit(fixture, HELPER);
        PunishmentApprovalLease lease = assertInstanceOf(
                PunishmentRequestResult.Leased.class,
                fixture.service().acquire(submitted.request().requestId(), MOD)
        ).lease();

        PunishmentRequestResult.Approved approved = assertInstanceOf(
                PunishmentRequestResult.Approved.class,
                fixture.service().approve(lease, MOD)
        );

        assertEquals(PunishmentRequestStatus.APPROVED, approved.request().status());
        assertEquals(approved.caseId(), approved.request().resultingCaseId());
    }

    private static PunishmentRequestResult.Submitted submit(Fixture fixture, Actor requester) {
        return assertInstanceOf(
                PunishmentRequestResult.Submitted.class,
                fixture.service().submit(request(requester), OperationalMode.ACTIVE)
        );
    }

    private static Fixture fixture(StaffRank requiredRank, SanctionLength length) {
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
                requiredRank,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        PunishmentService punishments = new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom(new byte[]{1, 2, 3, 4})),
                authorization,
                new AtomicReasonPolicyRepository("v1", List.of(policy)),
                new NoOpModerationStore(),
                new EscalationEngine()
        );
        InMemoryRequestStore requests = new InMemoryRequestStore();
        PunishmentRequestService service = new PunishmentRequestService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7),
                Duration.ofMinutes(2),
                () -> REQUEST_ID,
                new SecureIdentifiers(new SecureRandom(new byte[]{5, 6, 7, 8})),
                authorization,
                punishments,
                requests
        );
        return new Fixture(service, requests);
    }

    private static CreatePunishmentRequest request(Actor actor) {
        return new CreatePunishmentRequest(
                new IdempotencyKey("punishment-request-test:" + actor.id()),
                TARGET_ID,
                actor,
                "test.reason",
                "Evidence-backed request",
                CaseVisibility.PUBLIC,
                List.of()
        );
    }

    private static Actor actor(String id, StaffRank rank) {
        return new Actor(UUID.fromString(id), rank.name(), rank);
    }

    private record Fixture(PunishmentRequestService service, InMemoryRequestStore requests) {
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

    private static final class InMemoryRequestStore implements PunishmentRequestStore {
        private final Map<UUID, PunishmentApprovalRequest> requests = new LinkedHashMap<>();
        private long fence;

        @Override
        public PunishmentRequestResult.Submitted submit(PunishmentApprovalRequest request) {
            PunishmentApprovalRequest existing = requests.values().stream()
                    .filter(value -> value.submissionKey().equals(request.submissionKey())
                            || value.proposal().matchKey().equals(request.proposal().matchKey())
                            && value.status() == PunishmentRequestStatus.PENDING)
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return new PunishmentRequestResult.Submitted(existing, true);
            }
            requests.put(request.requestId(), request);
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
            PunishmentApprovalRequest request = requests.get(requestId);
            if (request == null || !request.pendingAt(now)) {
                return Optional.empty();
            }
            fence++;
            PunishmentApprovalRequest updated = copy(request, request.status(), request.revision() + 1,
                    null, null, null, null);
            requests.put(requestId, updated);
            return Optional.of(new PunishmentApprovalLease(updated, ownerId, fence, leaseExpiresAt));
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            PunishmentApprovalRequest current = requests.get(lease.request().requestId());
            if (current == null || current.status() != PunishmentRequestStatus.PENDING) {
                return new PunishmentRequestResult.Rejected("REQUEST_NOT_PENDING", "Request is not pending");
            }
            PunishmentApprovalRequest approved = copy(
                    current,
                    PunishmentRequestStatus.APPROVED,
                    current.revision() + 1,
                    approver.id(),
                    "Approved",
                    caseId,
                    now
            );
            requests.put(approved.requestId(), approved);
            return new PunishmentRequestResult.Approved(approved, caseId, false);
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            PunishmentApprovalRequest current = requests.get(lease.request().requestId());
            if (current == null || current.status() != PunishmentRequestStatus.PENDING) {
                return new PunishmentRequestResult.Rejected("REQUEST_NOT_PENDING", "Request is not pending");
            }
            PunishmentApprovalRequest denied = copy(
                    current,
                    PunishmentRequestStatus.DENIED,
                    current.revision() + 1,
                    approver.id(),
                    note,
                    null,
                    now
            );
            requests.put(denied.requestId(), denied);
            return new PunishmentRequestResult.Denied(denied, false);
        }

        @Override
        public int expire(Instant now) {
            List<PunishmentApprovalRequest> expired = new ArrayList<>();
            requests.values().stream()
                    .filter(request -> request.status() == PunishmentRequestStatus.PENDING
                            && !request.expiresAt().isAfter(now))
                    .forEach(expired::add);
            expired.forEach(request -> requests.put(request.requestId(), copy(
                    request,
                    PunishmentRequestStatus.EXPIRED,
                    request.revision() + 1,
                    null,
                    "Expired",
                    null,
                    now
            )));
            return expired.size();
        }

        private static PunishmentApprovalRequest copy(
                PunishmentApprovalRequest request,
                PunishmentRequestStatus status,
                long revision,
                UUID resolvedBy,
                String note,
                CaseId caseId,
                Instant resolvedAt
        ) {
            return new PunishmentApprovalRequest(
                    request.requestId(),
                    request.submissionKey(),
                    request.proposal(),
                    request.createdAt(),
                    request.expiresAt(),
                    status,
                    revision,
                    resolvedBy,
                    note,
                    caseId,
                    resolvedAt
            );
        }
    }
}
