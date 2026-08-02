package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

class PunishmentRequestOperationalModeTest {
    private static final Instant NOW = Instant.parse("2026-08-01T20:00:00Z");
    private static final Actor HELPER = actor("00000000-0000-0000-0000-000000000801", StaffRank.HELPER);
    private static final Actor MOD = actor("00000000-0000-0000-0000-000000000802", StaffRank.MOD);

    @Test
    void maintenanceBlocksEveryRequestMutationBeforeStoreAccess() {
        AtomicReference<OperationalMode> mode = new AtomicReference<>(OperationalMode.ACTIVE);
        TrackingRequestStore store = new TrackingRequestStore();
        PunishmentRequestService service = service(mode, store);
        PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                PunishmentRequestResult.Submitted.class,
                service.submit(request(), OperationalMode.ACTIVE)
        );
        PunishmentApprovalLease lease = assertInstanceOf(
                PunishmentRequestResult.Leased.class,
                service.acquire(submitted.request().requestId(), MOD)
        ).lease();
        store.mutations.set(0);
        mode.set(OperationalMode.MAINTENANCE);

        assertModeBlocked(service.acquire(submitted.request().requestId(), MOD));
        assertModeBlocked(service.approve(lease, MOD));
        assertModeBlocked(service.deny(lease, MOD, "Maintenance fence"));
        assertEquals(0, service.expire());
        assertEquals(0, store.mutations.get());
    }

    private static PunishmentRequestService service(
            AtomicReference<OperationalMode> mode,
            TrackingRequestStore store
    ) {
        PunishmentStep step = new PunishmentStep(0, "Configured", List.of(
                new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent())
        ));
        ReasonPolicy policy = new ReasonPolicy(
                "test.mode",
                "test",
                "Test mode guard",
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
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        SecureIdentifiers identifiers = new SecureIdentifiers(new SecureRandom());
        PunishmentService punishments = new PunishmentService(
                clock,
                identifiers,
                authorization,
                new AtomicReasonPolicyRepository("v1", List.of(policy)),
                new NoOpModerationStore(),
                new EscalationEngine()
        );
        return new PunishmentRequestService(
                clock,
                Duration.ofDays(7),
                Duration.ofMinutes(2),
                identifiers,
                authorization,
                punishments,
                store,
                mode::get
        );
    }

    private static CreatePunishmentRequest request() {
        return new CreatePunishmentRequest(
                new IdempotencyKey("punishment-request-mode-test"),
                UUID.fromString("00000000-0000-0000-0000-000000000803"),
                HELPER,
                "test.mode",
                "Evidence-backed mode guard request",
                CaseVisibility.PUBLIC,
                List.of()
        );
    }

    private static void assertModeBlocked(PunishmentRequestResult result) {
        PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                PunishmentRequestResult.Rejected.class,
                result
        );
        assertEquals("MODE_BLOCKED", rejected.code());
    }

    private static Actor actor(String id, StaffRank rank) {
        return new Actor(UUID.fromString(id), rank.name(), rank);
    }

    private static final class TrackingRequestStore implements PunishmentRequestStore {
        private final AtomicInteger mutations = new AtomicInteger();
        private PunishmentApprovalRequest request;

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest submitted) {
            mutations.incrementAndGet();
            request = submitted;
            return new PunishmentRequestResult.Submitted(submitted, false);
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return Optional.ofNullable(request);
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            return request == null ? List.of() : List.of(request);
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            mutations.incrementAndGet();
            return Optional.of(new PunishmentApprovalLease(request, ownerId, 1, leaseExpiresAt));
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            mutations.incrementAndGet();
            return new PunishmentRequestResult.Rejected("UNEXPECTED", "Store approval should not run");
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            mutations.incrementAndGet();
            return new PunishmentRequestResult.Rejected("UNEXPECTED", "Store denial should not run");
        }

        @Override
        public int expire(Instant now) {
            mutations.incrementAndGet();
            return 1;
        }

        @Override
        public int expire(Instant now, int limit) {
            mutations.incrementAndGet();
            return 1;
        }
    }

    private static final class NoOpModerationStore implements ModerationStore {
        @Override
        public List<PriorOffense> relatedHistory(UUID targetId, String family) {
            return List.of();
        }

        @Override
        public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
            return new PunishmentResult.Accepted(new CaseId("A000000000000801"), false);
        }
    }
}
