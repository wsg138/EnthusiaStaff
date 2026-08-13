package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.inventory.InventoryRecoveryResult;
import net.enthusia.staff.domain.ports.InventoryRecoveryStore;
import org.junit.jupiter.api.Test;

final class InventoryRecoveryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-11T20:00:00Z");
    private static final CaseId CASE_ID = new CaseId("01J0000000008001");

    @Test
    void nonFounderCannotReachRecoveryStore() {
        AtomicInteger calls = new AtomicInteger();
        InventoryRecoveryStore store = (caseId, actorId, now) -> {
            calls.incrementAndGet();
            return requeued();
        };
        InventoryRecoveryCoordinator coordinator = coordinator(store);

        InventoryRecoveryResult result = coordinator.recover(
                new Actor(UUID.randomUUID(), "Admin", StaffRank.ADMIN),
                CASE_ID
        );

        assertEquals(InventoryRecoveryResult.Status.UNAUTHORIZED, result.status());
        assertEquals(0, calls.get());
    }

    @Test
    void unresolvedActorCannotReachRecoveryStore() {
        AtomicInteger calls = new AtomicInteger();
        InventoryRecoveryStore store = (caseId, actorId, now) -> {
            calls.incrementAndGet();
            return requeued();
        };
        InventoryRecoveryCoordinator coordinator = coordinator(store);

        InventoryRecoveryResult result = coordinator.recover(null, CASE_ID);

        assertEquals(InventoryRecoveryResult.Status.UNAUTHORIZED, result.status());
        assertEquals(0, calls.get());
    }

    @Test
    void founderAuthorizationDelegatesExactCaseActorAndClock() {
        UUID actorId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        InventoryRecoveryStore store = (caseId, actualActorId, now) -> {
            assertEquals(CASE_ID, caseId);
            assertEquals(actorId, actualActorId);
            assertEquals(NOW, now);
            calls.incrementAndGet();
            return requeued();
        };
        InventoryRecoveryCoordinator coordinator = coordinator(store);

        InventoryRecoveryResult result = coordinator.recover(
                new Actor(actorId, "Founder", StaffRank.FOUNDER),
                CASE_ID
        );

        assertEquals(InventoryRecoveryResult.Status.REQUEUED, result.status());
        assertEquals(1, calls.get());
    }

    @Test
    void missingStorageFailsClosed() {
        InventoryRecoveryCoordinator coordinator = new InventoryRecoveryCoordinator(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> null,
                new DefaultAuthorizationPolicy()
        );

        InventoryRecoveryResult result = coordinator.recover(
                new Actor(UUID.randomUUID(), "Founder", StaffRank.FOUNDER),
                CASE_ID
        );

        assertEquals(InventoryRecoveryResult.Status.STORAGE_UNAVAILABLE, result.status());
    }

    private static InventoryRecoveryCoordinator coordinator(InventoryRecoveryStore store) {
        return new InventoryRecoveryCoordinator(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> store,
                new DefaultAuthorizationPolicy()
        );
    }

    private static InventoryRecoveryResult requeued() {
        return new InventoryRecoveryResult(
                InventoryRecoveryResult.Status.REQUEUED,
                Optional.of(UUID.randomUUID()),
                "requeued"
        );
    }
}
