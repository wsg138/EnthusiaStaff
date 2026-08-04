package net.enthusia.staff.paper;

import java.util.Objects;

final class PaperShutdownCoordinator {
    private final Runnable closeOperationalRuntime;
    private final Runnable closeNonDatabaseResources;
    private final Runnable drainWorkers;
    private final Runnable markStaffSessionsForRecovery;
    private final Runnable closeDatabase;

    PaperShutdownCoordinator(
            Runnable closeOperationalRuntime,
            Runnable closeNonDatabaseResources,
            Runnable drainWorkers,
            Runnable markStaffSessionsForRecovery,
            Runnable closeDatabase
    ) {
        this.closeOperationalRuntime = Objects.requireNonNull(
                closeOperationalRuntime, "closeOperationalRuntime");
        this.closeNonDatabaseResources = Objects.requireNonNull(
                closeNonDatabaseResources, "closeNonDatabaseResources");
        this.drainWorkers = Objects.requireNonNull(drainWorkers, "drainWorkers");
        this.markStaffSessionsForRecovery = Objects.requireNonNull(
                markStaffSessionsForRecovery, "markStaffSessionsForRecovery");
        this.closeDatabase = Objects.requireNonNull(closeDatabase, "closeDatabase");
    }

    void shutdown() {
        RuntimeException failure = run(closeOperationalRuntime, null);
        failure = run(closeNonDatabaseResources, failure);
        failure = run(drainWorkers, failure);
        failure = run(markStaffSessionsForRecovery, failure);
        failure = run(closeDatabase, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException run(Runnable operation, RuntimeException previous) {
        try {
            operation.run();
            return previous;
        } catch (RuntimeException exception) {
            if (previous == null) {
                return exception;
            }
            previous.addSuppressed(exception);
            return previous;
        }
    }
}
