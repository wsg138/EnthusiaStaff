package net.enthusia.staff.paper;

import java.util.Objects;

final class PaperShutdownCoordinator {
    private final Runnable closeOperationalRuntime;
    private final Runnable closeNonDatabaseResources;
    private final Runnable drainWorkers;
    private final Runnable closeDatabase;

    PaperShutdownCoordinator(
            Runnable closeOperationalRuntime,
            Runnable closeNonDatabaseResources,
            Runnable drainWorkers,
            Runnable closeDatabase
    ) {
        this.closeOperationalRuntime = Objects.requireNonNull(
                closeOperationalRuntime, "closeOperationalRuntime");
        this.closeNonDatabaseResources = Objects.requireNonNull(
                closeNonDatabaseResources, "closeNonDatabaseResources");
        this.drainWorkers = Objects.requireNonNull(drainWorkers, "drainWorkers");
        this.closeDatabase = Objects.requireNonNull(closeDatabase, "closeDatabase");
    }

    void shutdown() {
        closeOperationalRuntime.run();
        closeNonDatabaseResources.run();
        drainWorkers.run();
        closeDatabase.run();
    }
}
