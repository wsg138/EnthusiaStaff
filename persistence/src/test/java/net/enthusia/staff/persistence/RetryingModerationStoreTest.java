package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;
import org.junit.jupiter.api.Test;

class RetryingModerationStoreTest {
    @Test
    void retriesMariaDbDeadlocksWithBoundedBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> pauses = new ArrayList<>();
        PunishmentResult.Accepted expected = new PunishmentResult.Accepted(
                new CaseId("A000000000000301"),
                false
        );
        ModerationStore delegate = store(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw deadlock();
            }
            return expected;
        });

        RetryingModerationStore retrying = new RetryingModerationStore(delegate, pauses::add);

        assertSame(expected, retrying.createPunishment(null));
        assertEquals(3, attempts.get());
        assertEquals(List.of(1, 2), pauses);
    }

    @Test
    void doesNotRetryUnrelatedSqlFailures() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> pauses = new ArrayList<>();
        ModerationPersistenceException failure = new ModerationPersistenceException(
                "unrelated",
                new SQLException("failure", "HY000", 1205)
        );
        ModerationStore delegate = store(() -> {
            attempts.incrementAndGet();
            throw failure;
        });

        RetryingModerationStore retrying = new RetryingModerationStore(delegate, pauses::add);

        assertSame(failure, assertThrows(
                ModerationPersistenceException.class,
                () -> retrying.createPunishment(null)
        ));
        assertEquals(1, attempts.get());
        assertEquals(List.of(), pauses);
    }

    @Test
    void stopsAfterThreeDeadlockedTransactions() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> pauses = new ArrayList<>();
        ModerationStore delegate = store(() -> {
            attempts.incrementAndGet();
            throw deadlock();
        });

        RetryingModerationStore retrying = new RetryingModerationStore(delegate, pauses::add);

        assertThrows(ModerationPersistenceException.class, () -> retrying.createPunishment(null));
        assertEquals(3, attempts.get());
        assertEquals(List.of(1, 2), pauses);
    }

    @Test
    void committedCaseLookupDelegatesReadOnlyWithoutStartingPunishmentTransaction() {
        CaseId existing = new CaseId("A000000000000311");
        IdempotencyKey key = new IdempotencyKey("punishment-draft:existing");
        AtomicInteger writes = new AtomicInteger();
        ModerationStore delegate = new ModerationStore() {
            @Override
            public List<PriorOffense> relatedHistory(UUID targetId, String family) {
                return List.of();
            }

            @Override
            public Optional<CaseId> committedCase(IdempotencyKey lookup) {
                return key.equals(lookup) ? Optional.of(existing) : Optional.empty();
            }

            @Override
            public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
                writes.incrementAndGet();
                throw new AssertionError("Recovery lookup must never create a new case");
            }
        };
        RetryingModerationStore retrying = new RetryingModerationStore(delegate);
        assertEquals(Optional.of(existing), retrying.committedCase(key));
        assertEquals(Optional.empty(),
                retrying.committedCase(new IdempotencyKey("punishment-draft:unknown")));
        assertEquals(0, writes.get());
    }

    private static ModerationStore store(PunishmentOperation operation) {
        return new ModerationStore() {
            @Override
            public List<PriorOffense> relatedHistory(UUID targetId, String family) {
                return List.of();
            }

            @Override
            public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
                return operation.create();
            }
        };
    }

    private static ModerationPersistenceException deadlock() {
        return new ModerationPersistenceException(
                "deadlock",
                new SQLException("deadlock", "40001", 1213)
        );
    }

    @FunctionalInterface
    private interface PunishmentOperation {
        PunishmentResult.Accepted create();
    }
}
