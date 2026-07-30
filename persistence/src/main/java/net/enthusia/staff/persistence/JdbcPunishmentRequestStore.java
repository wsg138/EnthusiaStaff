package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

public final class JdbcPunishmentRequestStore implements PunishmentRequestStore {
    private static final int MAXIMUM_EXPIRATIONS_PER_RUN = 1_000;

    private final DataSource dataSource;
    private final JdbcPunishmentRequestRepository repository;
    private final JdbcPunishmentRequestEvents events;
    private final JdbcModerationStore moderation;

    public JdbcPunishmentRequestStore(
            DataSource dataSource,
            ObjectMapper json,
            JdbcModerationStore moderation
    ) {
        if (dataSource == null || json == null || moderation == null) {
            throw new IllegalArgumentException("dataSource, json and moderation store must be present");
        }
        this.dataSource = dataSource;
        this.repository = new JdbcPunishmentRequestRepository(
                dataSource,
                new JdbcPunishmentRequestCodec(json)
        );
        this.events = new JdbcPunishmentRequestEvents(json);
        this.moderation = moderation;
    }

    @Override
    public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
        if (request == null || request.status() != PunishmentRequestStatus.PENDING) {
            throw new IllegalArgumentException("a pending punishment request must be present");
        }
        try {
            return JdbcTransactionSupport.execute(
                    dataSource,
                    "Unable to submit punishment request",
                    connection -> submit(connection, request)
            );
        } catch (ModerationPersistenceException exception) {
            PunishmentApprovalRequest existing = repository.replayAfterConflict(request);
            if (existing == null) {
                throw exception;
            }
            return submissionConflict(existing, request);
        }
    }

    @Override
    public Optional<PunishmentApprovalRequest> find(UUID requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("punishment request identifier must be present");
        }
        return repository.find(requestId);
    }

    @Override
    public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("current time and a limit between 1 and 500 are required");
        }
        return repository.pending(now, limit);
    }

    @Override
    public Optional<PunishmentApprovalLease> acquire(
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    ) {
        if (requestId == null || ownerId == null || now == null || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("valid punishment approval lease fields must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to acquire punishment approval lease",
                connection -> acquire(connection, requestId, ownerId, now, leaseExpiresAt)
        );
    }

    @Override
    public PunishmentRequestResult approve(
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId caseId,
            Instant now
    ) {
        validateDecision(lease, approver, now);
        if (caseId == null) {
            throw new IllegalArgumentException("resulting case identifier must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to approve punishment request",
                connection -> approve(connection, lease, approver, caseId, now)
        );
    }

    @Override
    public PunishmentRequestResult deny(
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    ) {
        validateDecision(lease, approver, now);
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("punishment request denial note must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to deny punishment request",
                connection -> deny(connection, lease, approver, note, now)
        );
    }

    @Override
    public int expire(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("current time must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to expire punishment requests",
                connection -> expire(connection, now)
        );
    }

    static int fulfillMatching(
            Connection connection,
            PunishmentPlan plan,
            CaseId caseId,
            Instant now,
            UUID excludedRequestId
    ) throws SQLException {
        return JdbcPunishmentRequestFulfillment.apply(
                connection,
                plan,
                caseId,
                now,
                excludedRequestId
        );
    }

    private PunishmentRequestResult submit(
            Connection connection,
            PunishmentApprovalRequest request
    ) throws SQLException {
        repository.lockTarget(connection, request.proposal().targetId(), request.createdAt());
        PunishmentApprovalRequest existing = repository.existingForSubmission(
                connection,
                request.submissionKey().value(),
                request.proposal().matchKey(),
                true
        );
        if (existing != null) {
            return submissionConflict(existing, request);
        }
        repository.insert(connection, request);
        events.submitted(connection, request);
        return new PunishmentRequestResult.Submitted(request, false);
    }

    private Optional<PunishmentApprovalLease> acquire(
            Connection connection,
            UUID requestId,
            UUID ownerId,
            Instant now,
            Instant leaseExpiresAt
    ) throws SQLException {
        PunishmentApprovalRequest request = repository.lock(connection, requestId);
        if (request == null || !request.pendingAt(now)) {
            return Optional.empty();
        }
        String resourceKey = JdbcPunishmentRequestFulfillment.resourceKey(requestId);
        long fence = JdbcOperationLeaseSupport.acquire(
                connection,
                resourceKey,
                ownerId,
                leaseExpiresAt,
                now
        );
        if (fence == JdbcOperationLeaseSupport.UNAVAILABLE) {
            return Optional.empty();
        }
        events.leaseAcquired(connection, requestId, ownerId, fence, now);
        return Optional.of(new PunishmentApprovalLease(request, ownerId, fence, leaseExpiresAt));
    }

    private PunishmentRequestResult approve(
            Connection connection,
            PunishmentApprovalLease lease,
            Actor approver,
            CaseId proposedCaseId,
            Instant now
    ) throws SQLException {
        repository.lockTarget(connection, lease.request().proposal().targetId(), now);
        PunishmentApprovalRequest current = repository.lock(connection, lease.request().requestId());
        if (current == null) {
            return rejected("REQUEST_NOT_FOUND", "The punishment request does not exist");
        }
        if (current.status() == PunishmentRequestStatus.APPROVED && current.resultingCaseId() != null) {
            return new PunishmentRequestResult.Approved(current, current.resultingCaseId(), true);
        }
        PunishmentRequestResult.Rejected issue = decisionStateIssue(
                connection,
                current,
                lease,
                approver,
                now
        );
        if (issue != null) {
            return issue;
        }
        PunishmentPlan plan = current.proposal().toPlan(
                proposedCaseId,
                approvalIdempotency(current.requestId()),
                approver,
                now
        );
        PunishmentResult.Accepted accepted = moderation.createPunishment(connection, plan);
        PunishmentApprovalRequest approved = repository.resolve(
                connection,
                current,
                PunishmentRequestStatus.APPROVED,
                approver.id(),
                "Approved by " + approver.displayName(),
                accepted.caseId(),
                now
        );
        events.approved(
                connection,
                approved,
                approver.id(),
                lease.fenceToken(),
                accepted.caseId(),
                now
        );
        release(connection, current.requestId(), approver.id(), lease.fenceToken());
        fulfillMatching(connection, plan, accepted.caseId(), now, current.requestId());
        return new PunishmentRequestResult.Approved(approved, accepted.caseId(), accepted.replayed());
    }

    private PunishmentRequestResult deny(
            Connection connection,
            PunishmentApprovalLease lease,
            Actor approver,
            String note,
            Instant now
    ) throws SQLException {
        PunishmentApprovalRequest current = repository.lock(connection, lease.request().requestId());
        if (current == null) {
            return rejected("REQUEST_NOT_FOUND", "The punishment request does not exist");
        }
        if (current.status() == PunishmentRequestStatus.DENIED) {
            return new PunishmentRequestResult.Denied(current, true);
        }
        PunishmentRequestResult.Rejected issue = decisionStateIssue(
                connection,
                current,
                lease,
                approver,
                now
        );
        if (issue != null) {
            return issue;
        }
        PunishmentApprovalRequest denied = repository.resolve(
                connection,
                current,
                PunishmentRequestStatus.DENIED,
                approver.id(),
                note,
                null,
                now
        );
        events.denied(connection, denied, approver.id(), lease.fenceToken(), note, now);
        release(connection, current.requestId(), approver.id(), lease.fenceToken());
        return new PunishmentRequestResult.Denied(denied, false);
    }

    private int expire(Connection connection, Instant now) throws SQLException {
        List<PunishmentApprovalRequest> expired = repository.expired(
                connection,
                now,
                MAXIMUM_EXPIRATIONS_PER_RUN
        );
        for (PunishmentApprovalRequest request : expired) {
            PunishmentApprovalRequest resolved = repository.resolve(
                    connection,
                    request,
                    PunishmentRequestStatus.EXPIRED,
                    null,
                    "Punishment request expired without a decision",
                    null,
                    now
            );
            events.expired(connection, resolved, now);
            deleteLease(connection, request.requestId());
        }
        return expired.size();
    }

    private PunishmentRequestResult.Rejected decisionStateIssue(
            Connection connection,
            PunishmentApprovalRequest current,
            PunishmentApprovalLease lease,
            Actor approver,
            Instant now
    ) throws SQLException {
        if (!current.pendingAt(now)) {
            return rejected("REQUEST_NOT_PENDING", "The punishment request is resolved or expired");
        }
        if (current.revision() != lease.request().revision()) {
            return rejected("STALE_REQUEST", "The punishment request changed after the lease was acquired");
        }
        boolean held = lease.ownerId().equals(approver.id()) && JdbcOperationLeaseSupport.holds(
                connection,
                JdbcPunishmentRequestFulfillment.resourceKey(current.requestId()),
                approver.id(),
                lease.fenceToken(),
                now
        );
        return held ? null : rejected(
                "STALE_LEASE",
                "The punishment approval lease is stale or belongs to another reviewer"
        );
    }

    private static PunishmentRequestResult submissionConflict(
            PunishmentApprovalRequest existing,
            PunishmentApprovalRequest attempted
    ) {
        if (existing.submissionKey().equals(attempted.submissionKey())) {
            if (existing.proposal().equals(attempted.proposal())) {
                return new PunishmentRequestResult.Submitted(existing, true);
            }
            return rejected(
                    "IDEMPOTENCY_CONFLICT",
                    "The punishment request submission key was already used for another proposal"
            );
        }
        return rejected(
                "DUPLICATE_PENDING",
                "An exact matching punishment request is already pending"
        );
    }

    private static void release(
            Connection connection,
            UUID requestId,
            UUID ownerId,
            long fenceToken
    ) throws SQLException {
        JdbcOperationLeaseSupport.release(
                connection,
                JdbcPunishmentRequestFulfillment.resourceKey(requestId),
                ownerId,
                fenceToken
        );
    }

    private static void deleteLease(Connection connection, UUID requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM operation_leases WHERE resource_key = ?")) {
            statement.setString(1, JdbcPunishmentRequestFulfillment.resourceKey(requestId));
            statement.executeUpdate();
        }
    }

    private static IdempotencyKey approvalIdempotency(UUID requestId) {
        return new IdempotencyKey("punishment-request:" + requestId + ":approved");
    }

    private static void validateDecision(PunishmentApprovalLease lease, Actor approver, Instant now) {
        if (lease == null || approver == null || now == null) {
            throw new IllegalArgumentException("punishment request decision fields must be present");
        }
    }

    private static PunishmentRequestResult.Rejected rejected(String code, String message) {
        return new PunishmentRequestResult.Rejected(code, message);
    }
}
