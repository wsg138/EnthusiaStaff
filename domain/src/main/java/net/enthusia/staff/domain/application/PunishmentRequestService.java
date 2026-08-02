package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.common.Checks;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

public final class PunishmentRequestService {
    private static final Duration MAXIMUM_REQUEST_LIFETIME = Duration.ofDays(30);
    private static final Duration MAXIMUM_LEASE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAXIMUM_QUERY_LIMIT = 500;
    private static final int DEFAULT_EXPIRATION_BATCH_SIZE = 100;

    private final Clock clock;
    private final Duration requestLifetime;
    private final Duration leaseLifetime;
    private final Supplier<UUID> requestIds;
    private final SecureIdentifiers caseIds;
    private final AuthorizationPolicy authorization;
    private final PunishmentService punishments;
    private final PunishmentRequestStore requests;
    private final Supplier<OperationalMode> operationalMode;
    private final int expirationBatchSize;

    public PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests
    ) {
        this(
                clock,
                requestLifetime,
                leaseLifetime,
                caseIds,
                authorization,
                punishments,
                requests,
                () -> OperationalMode.ACTIVE,
                DEFAULT_EXPIRATION_BATCH_SIZE
        );
    }

    public PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests,
            int expirationBatchSize
    ) {
        this(
                clock,
                requestLifetime,
                leaseLifetime,
                caseIds,
                authorization,
                punishments,
                requests,
                () -> OperationalMode.ACTIVE,
                expirationBatchSize
        );
    }

    public PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests,
            Supplier<OperationalMode> operationalMode
    ) {
        this(
                clock,
                requestLifetime,
                leaseLifetime,
                caseIds,
                authorization,
                punishments,
                requests,
                operationalMode,
                DEFAULT_EXPIRATION_BATCH_SIZE
        );
    }

    public PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests,
            Supplier<OperationalMode> operationalMode,
            int expirationBatchSize
    ) {
        this(
                clock,
                requestLifetime,
                leaseLifetime,
                UUID::randomUUID,
                caseIds,
                authorization,
                punishments,
                requests,
                operationalMode,
                expirationBatchSize
        );
    }

    PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            Supplier<UUID> requestIds,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests
    ) {
        this(
                clock,
                requestLifetime,
                leaseLifetime,
                requestIds,
                caseIds,
                authorization,
                punishments,
                requests,
                () -> OperationalMode.ACTIVE,
                DEFAULT_EXPIRATION_BATCH_SIZE
        );
    }

    PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            Supplier<UUID> requestIds,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests,
            int expirationBatchSize
    ) {
        this(
                clock,
                requestLifetime,
                leaseLifetime,
                requestIds,
                caseIds,
                authorization,
                punishments,
                requests,
                () -> OperationalMode.ACTIVE,
                expirationBatchSize
        );
    }

    PunishmentRequestService(
            Clock clock,
            Duration requestLifetime,
            Duration leaseLifetime,
            Supplier<UUID> requestIds,
            SecureIdentifiers caseIds,
            AuthorizationPolicy authorization,
            PunishmentService punishments,
            PunishmentRequestStore requests,
            Supplier<OperationalMode> operationalMode,
            int expirationBatchSize
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.requestLifetime = validateLifetime(
                requestLifetime,
                MAXIMUM_REQUEST_LIFETIME,
                "punishment request"
        );
        this.leaseLifetime = validateLifetime(
                leaseLifetime,
                MAXIMUM_LEASE_LIFETIME,
                "punishment approval lease"
        );
        this.requestIds = Objects.requireNonNull(requestIds);
        this.caseIds = Objects.requireNonNull(caseIds);
        this.authorization = Objects.requireNonNull(authorization);
        this.punishments = Objects.requireNonNull(punishments);
        this.requests = Objects.requireNonNull(requests);
        this.operationalMode = Objects.requireNonNull(operationalMode);
        if (expirationBatchSize < 1 || expirationBatchSize > MAXIMUM_QUERY_LIMIT) {
            throw new IllegalArgumentException("expiration batch size must be between 1 and 500");
        }
        this.expirationBatchSize = expirationBatchSize;
    }

    public PunishmentRequestResult submit(CreatePunishmentRequest request, OperationalMode mode) {
        return submitConfirmed(request, mode, null);
    }

    public PunishmentRequestResult submitConfirmed(
            CreatePunishmentRequest request,
            OperationalMode mode,
            PunishmentExpectation expectation
    ) {
        Objects.requireNonNull(request);
        PunishmentEvaluation evaluation = punishments.evaluateRequestProposal(request, mode);
        if (evaluation instanceof PunishmentEvaluation.Rejected rejected) {
            return new PunishmentRequestResult.Rejected(rejected.code(), rejected.message());
        }
        PunishmentAssessment assessment = ((PunishmentEvaluation.Allowed) evaluation).assessment();
        if (expectation != null && !expectation.matches(assessment)) {
            return new PunishmentRequestResult.Rejected(
                    "RECOMMENDATION_CHANGED",
                    "The authoritative recommendation changed; review again before submitting"
            );
        }
        if (!punishments.requiresApproval(request.actor(), assessment)) {
            return new PunishmentRequestResult.Rejected(
                    "APPROVAL_NOT_REQUIRED",
                    "This configured punishment can be applied directly by the requester"
            );
        }
        return submitEvaluated(request, assessment);
    }

    public List<PunishmentApprovalRequest> pending(int limit) {
        validateQueryLimit(limit);
        Instant now = clock.instant();
        return requests.pending(now, limit);
    }

    public Optional<PunishmentApprovalRequest> find(UUID requestId) {
        Objects.requireNonNull(requestId);
        return requests.find(requestId);
    }

    public List<PunishmentApprovalRequest> reviewable(Actor approver, int limit) {
        validateQueryLimit(limit);
        if (!hasApprovalAuthority(approver)) {
            return List.of();
        }
        return pending(MAXIMUM_QUERY_LIMIT).stream()
                .filter(request -> mayReview(approver, request))
                .limit(limit)
                .toList();
    }

    public boolean mayReview(Actor approver, PunishmentApprovalRequest request) {
        return hasApprovalAuthority(approver)
                && request != null
                && !request.proposal().requester().id().equals(approver.id())
                && meetsRequiredApprovalRank(approver.rank(), request.proposal().requiredRank());
    }

    public PunishmentRequestResult acquire(UUID requestId, Actor approver) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approver);
        PunishmentRequestResult.Rejected modeIssue = writeModeRejection();
        if (modeIssue != null) {
            return modeIssue;
        }
        Instant now = clock.instant();
        PunishmentApprovalRequest request = requests.find(requestId).orElse(null);
        PunishmentRequestResult.Rejected rejection = approvalRejection(request, approver, now);
        if (rejection != null) {
            return rejection;
        }
        return requests.acquire(requestId, approver.id(), now, now.plus(leaseLifetime))
                .<PunishmentRequestResult>map(PunishmentRequestResult.Leased::new)
                .orElseGet(() -> new PunishmentRequestResult.Rejected(
                        "LEASE_UNAVAILABLE",
                        "The punishment request is no longer pending or is being reviewed elsewhere"
                ));
    }

    public PunishmentRequestResult approve(PunishmentApprovalLease lease, Actor approver) {
        Objects.requireNonNull(lease);
        Objects.requireNonNull(approver);
        PunishmentRequestResult.Rejected modeIssue = writeModeRejection();
        if (modeIssue != null) {
            return modeIssue;
        }
        Instant now = clock.instant();
        PunishmentRequestResult.Rejected rejection = leaseRejection(lease, approver, now);
        if (rejection != null) {
            return rejection;
        }
        return requests.approve(lease, approver, caseIds.newCaseId(), now);
    }

    public PunishmentRequestResult deny(
            PunishmentApprovalLease lease,
            Actor approver,
            String note
    ) {
        Objects.requireNonNull(lease);
        Objects.requireNonNull(approver);
        String normalizedNote = Checks.nonBlank(note, "denialNote", 1_000);
        PunishmentRequestResult.Rejected modeIssue = writeModeRejection();
        if (modeIssue != null) {
            return modeIssue;
        }
        Instant now = clock.instant();
        PunishmentRequestResult.Rejected rejection = leaseRejection(lease, approver, now);
        if (rejection != null) {
            return rejection;
        }
        return requests.deny(lease, approver, normalizedNote, now);
    }

    public int expire() {
        return writeModeRejection() == null
                ? requests.expire(clock.instant(), expirationBatchSize)
                : 0;
    }

    private PunishmentRequestResult submitEvaluated(
            CreatePunishmentRequest request,
            PunishmentAssessment assessment
    ) {
        Instant now = clock.instant();
        PunishmentApprovalRequest pending = PunishmentApprovalRequest.pending(
                Objects.requireNonNull(requestIds.get(), "generated punishment request identifier"),
                request.idempotencyKey(),
                PunishmentProposal.from(request, assessment),
                now,
                now.plus(requestLifetime)
        );
        return requests.submit(pending);
    }

    private PunishmentRequestResult.Rejected leaseRejection(
            PunishmentApprovalLease lease,
            Actor approver,
            Instant now
    ) {
        if (!lease.ownerId().equals(approver.id())) {
            return new PunishmentRequestResult.Rejected(
                    "LEASE_OWNER_MISMATCH",
                    "The approval lease belongs to another staff member"
            );
        }
        if (!lease.validAt(now)) {
            return new PunishmentRequestResult.Rejected(
                    "LEASE_EXPIRED",
                    "The approval lease or punishment request has expired"
            );
        }
        return approvalRejection(lease.request(), approver, now);
    }

    private PunishmentRequestResult.Rejected approvalRejection(
            PunishmentApprovalRequest request,
            Actor approver,
            Instant now
    ) {
        if (!hasApprovalAuthority(approver)) {
            return new PunishmentRequestResult.Rejected(
                    "FORBIDDEN",
                    "Only Mod, Admin, or Founder may decide punishment requests"
            );
        }
        if (request == null) {
            return new PunishmentRequestResult.Rejected(
                    "REQUEST_NOT_FOUND",
                    "The punishment request does not exist"
            );
        }
        if (!request.pendingAt(now)) {
            return new PunishmentRequestResult.Rejected(
                    "REQUEST_NOT_PENDING",
                    "The punishment request is resolved or expired"
            );
        }
        if (request.proposal().requester().id().equals(approver.id())) {
            return new PunishmentRequestResult.Rejected(
                    "SELF_APPROVAL_FORBIDDEN",
                    "A requester cannot approve or deny their own punishment request"
            );
        }
        if (!meetsRequiredApprovalRank(approver.rank(), request.proposal().requiredRank())) {
            return new PunishmentRequestResult.Rejected(
                    "APPROVER_RANK_REQUIRED",
                    request.proposal().requiredRank() + " or higher is required to approve this reason"
            );
        }
        return null;
    }

    private PunishmentRequestResult.Rejected writeModeRejection() {
        OperationalMode mode = Objects.requireNonNull(
                operationalMode.get(),
                "current operational mode"
        );
        if (mode == OperationalMode.ACTIVE) {
            return null;
        }
        return new PunishmentRequestResult.Rejected(
                "MODE_BLOCKED",
                "Punishment request changes are disabled in " + mode
        );
    }

    private boolean hasApprovalAuthority(Actor approver) {
        return approver != null
                && authorization.permits(approver, ModerationAction.APPROVE_POLICY_SANCTION)
                && approver.rank().canApprovePunishmentRequests();
    }

    private static boolean meetsRequiredApprovalRank(StaffRank approver, StaffRank required) {
        return switch (required) {
            case HELPER, MOD -> approver.canApprovePunishmentRequests();
            case ADMIN -> approver == StaffRank.ADMIN || approver == StaffRank.FOUNDER;
            case FOUNDER -> approver == StaffRank.FOUNDER;
            case DEVELOPER, SYSTEM -> false;
        };
    }

    private static void validateQueryLimit(int limit) {
        if (limit < 1 || limit > MAXIMUM_QUERY_LIMIT) {
            throw new IllegalArgumentException("punishment request limit must be between 1 and 500");
        }
    }

    private static Duration validateLifetime(Duration lifetime, Duration maximum, String label) {
        Objects.requireNonNull(lifetime);
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(label + " lifetime must be positive and at most " + maximum);
        }
        return lifetime;
    }
}
