package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class FoliaPlayerHandoffTest {
    @Test
    void availabilityIsReadOnlyInsideOwnedTask() {
        AtomicInteger onlineChecks = new AtomicInteger();
        AtomicInteger operations = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        AtomicReference<Runnable> owned = new AtomicReference<>();

        FoliaPlayerHandoff.dispatchResolved(
                () -> {
                    onlineChecks.incrementAndGet();
                    return true;
                },
                (operation, retirement) -> {
                    owned.set(operation);
                    return true;
                },
                operations::incrementAndGet,
                retired::incrementAndGet
        );

        assertEquals(0, onlineChecks.get());
        assertEquals(0, operations.get());
        assertNotNull(owned.get());
        owned.get().run();

        assertEquals(1, onlineChecks.get());
        assertEquals(1, operations.get());
        assertEquals(0, retired.get());
    }

    @Test
    void offlineOwnedTaskRetiresWithoutRunningOperation() {
        AtomicInteger operations = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        AtomicReference<Runnable> owned = new AtomicReference<>();

        FoliaPlayerHandoff.dispatchResolved(
                () -> false,
                (operation, retirement) -> {
                    owned.set(operation);
                    return true;
                },
                operations::incrementAndGet,
                retired::incrementAndGet
        );

        owned.get().run();
        assertEquals(0, operations.get());
        assertEquals(1, retired.get());
    }

    @Test
    void rejectedSchedulerSettlesRetirementExactlyOnce() {
        AtomicInteger onlineChecks = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();

        FoliaPlayerHandoff.dispatchResolved(
                () -> {
                    onlineChecks.incrementAndGet();
                    return true;
                },
                (operation, retirement) -> {
                    retirement.run();
                    retirement.run();
                    return false;
                },
                () -> { },
                retired::incrementAndGet
        );

        assertEquals(0, onlineChecks.get());
        assertEquals(1, retired.get());
    }

    @Test
    void completedOwnerTaskIgnoresLateRetirementAndDuplicateExecution() {
        AtomicInteger operations = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        AtomicReference<Runnable> owned = new AtomicReference<>();
        AtomicReference<Runnable> retirement = new AtomicReference<>();

        FoliaPlayerHandoff.dispatchResolved(
                () -> true,
                (operation, retiredCallback) -> {
                    owned.set(operation);
                    retirement.set(retiredCallback);
                    return true;
                },
                operations::incrementAndGet,
                retired::incrementAndGet
        );

        owned.get().run();
        owned.get().run();
        retirement.get().run();

        assertEquals(1, operations.get());
        assertEquals(0, retired.get());
    }

    @Test
    void schedulerExceptionRetiresExactlyOnce() {
        AtomicInteger retired = new AtomicInteger();

        FoliaPlayerHandoff.dispatchResolved(
                () -> true,
                (operation, retirement) -> {
                    throw new IllegalStateException("retired");
                },
                () -> { },
                retired::incrementAndGet
        );

        assertEquals(1, retired.get());
    }
}
