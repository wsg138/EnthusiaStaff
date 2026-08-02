package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import org.junit.jupiter.api.Test;

class RetryingPunishmentRequestStoreTest {
    private static final PunishmentRequestResult.Rejected EXPECTED =
            new PunishmentRequestResult.Rejected("TEST", "test result");

    @Test
    void retriesEveryTransactionalEntryPointAfterMariaDbDeadlock() {
        List<Integer> pauses = new ArrayList<>();
        RetryOnceStore delegate = new RetryOnceStore();
        RetryingPunishmentRequestStore retrying = new RetryingPunishmentRequestStore(
                delegate,
                pauses::add
        );

        assertSame(EXPECTED, retrying.submit(null));
        assertEquals(Optional.empty(), retrying.acquire(null, null, null, null));
        assertSame(EXPECTED, retrying.approve(null, null, null, null));
        assertSame(EXPECTED, retrying.deny(null, null, null, null));
        assertEquals(7, retrying.expire(Instant.EPOCH));
        assertEquals(11, retrying.expire(Instant.EPOCH, 20));

        assertEquals(2, delegate.submitAttempts.get());
        assertEquals(2, delegate.acquireAttempts.get());
        assertEquals(2, delegate.approveAttempts.get());
        assertEquals(2, delegate.denyAttempts.get());
        assertEquals(2, delegate.expireAttempts.get());
        assertEquals(2, delegate.boundedExpireAttempts.get());
        assertEquals(List.of(1, 1, 1, 1, 1, 1), pauses);
    }

    @Test
    void doesNotRetryUnrelatedSqlFailure() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> pauses = new ArrayList<>();
        ModerationPersistenceException failure = new ModerationPersistenceException(
                "unrelated",
                new SQLException("lock timeout", "HY000", 1205)
        );
        PunishmentRequestStore delegate = approving(() -> {
            attempts.incrementAndGet();
            throw failure;
        });
        RetryingPunishmentRequestStore retrying = new RetryingPunishmentRequestStore(
                delegate,
                pauses::add
        );

        assertSame(failure, assertThrows(
                ModerationPersistenceException.class,
                () -> retrying.approve(null, null, null, null)
        ));
        assertEquals(1, attempts.get());
        assertEquals(List.of(), pauses);
    }

    @Test
    void stopsAfterThreeDeadlockedRequestTransactions() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> pauses = new ArrayList<>();
        PunishmentRequestStore delegate = approving(() -> {
            attempts.incrementAndGet();
            throw deadlock();
        });
        RetryingPunishmentRequestStore retrying = new RetryingPunishmentRequestStore(
                delegate,
                pauses::add
        );

        assertThrows(
                ModerationPersistenceException.class,
                () -> retrying.approve(null, null, null, null)
        );
        assertEquals(3, attempts.get());
        assertEquals(List.of(1, 2), pauses);
    }

    private static PunishmentRequestStore approving(Supplier<PunishmentRequestResult> approval) {
        return new PunishmentRequestStore() {
            @Override
            public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
                return EXPECTED;
            }

            @Override
            public Optional<PunishmentApprovalRequest> find(UUID requestId) {
                return Optional.empty();
            }

            @Override
            public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
                return List.of();
            }

            @Override
            public Optional<PunishmentApprovalLease> acquire(
                    UUID requestId,
                    UUID ownerId,
                    Instant now,
                    Instant leaseExpiresAt
            ) {
                return Optional.empty();
            }

            @Override
            public PunishmentRequestResult approve(
                    PunishmentApprovalLease lease,
                    Actor approver,
                    CaseId caseId,
                    Instant now
            ) {
                return approval.get();
            }

            @Override
            public PunishmentRequestResult deny(
                    PunishmentApprovalLease lease,
                    Actor approver,
                    String note,
                    Instant now
            ) {
                return EXPECTED;
            }

            @Override
            public int expire(Instant now) {
                return 0;
            }
        };
    }

    private static ModerationPersistenceException deadlock() {
        return new ModerationPersistenceException(
                "deadlock",
                new SQLException("deadlock", "40001", 1213)
        );
    }

    private static final class RetryOnceStore implements PunishmentRequestStore {
        private final AtomicInteger submitAttempts = new AtomicInteger();
        private final AtomicInteger acquireAttempts = new AtomicInteger();
        private final AtomicInteger approveAttempts = new AtomicInteger();
        private final AtomicInteger denyAttempts = new AtomicInteger();
        private final AtomicInteger expireAttempts = new AtomicInteger();
        private final AtomicInteger boundedExpireAttempts = new AtomicInteger();

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            return afterDeadlock(submitAttempts, () -> EXPECTED);
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            return Optional.empty();
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            return List.of();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            return afterDeadlock(acquireAttempts, Optional::empty);
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            return afterDeadlock(approveAttempts, () -> EXPECTED);
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            return afterDeadlock(denyAttempts, () -> EXPECTED);
        }

        @Override
        public int expire(Instant now) {
            return afterDeadlock(expireAttempts, () -> 7);
        }

        @Override
        public int expire(Instant now, int limit) {
            return afterDeadlock(boundedExpireAttempts, () -> 11);
        }

        private static <T> T afterDeadlock(AtomicInteger attempts, Supplier<T> result) {
            if (attempts.incrementAndGet() == 1) {
                throw deadlock();
            }
            return result.get();
        }
    }
}
