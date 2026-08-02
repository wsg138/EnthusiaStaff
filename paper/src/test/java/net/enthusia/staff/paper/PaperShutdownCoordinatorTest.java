package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperShutdownCoordinatorTest {
    @Test
    void alertLifecycleClosesBeforeWorkerDrainAndDatabaseCloseOccursLast() {
        List<String> events = new ArrayList<>();
        PaperShutdownCoordinator coordinator = new PaperShutdownCoordinator(
                () -> events.add("alerts-and-schedulers-closed"),
                () -> events.add("other-resources-closed"),
                () -> events.add("workers-drained"),
                () -> events.add("mariadb-closed")
        );

        coordinator.shutdown();

        assertEquals(List.of(
                "alerts-and-schedulers-closed",
                "other-resources-closed",
                "workers-drained",
                "mariadb-closed"
        ), events);
    }
    @Test
    void laterShutdownPhasesStillRunWhenEarlierCleanupFails() {
        List<String> events = new ArrayList<>();
        PaperShutdownCoordinator coordinator = new PaperShutdownCoordinator(
                () -> {
                    events.add("alerts-and-schedulers-closed");
                    throw new IllegalStateException("alert close failure");
                },
                () -> events.add("other-resources-closed"),
                () -> {
                    events.add("workers-drained");
                    throw new IllegalArgumentException("worker drain failure");
                },
                () -> events.add("mariadb-closed")
        );

        RuntimeException failure = assertThrows(RuntimeException.class, coordinator::shutdown);

        assertEquals("alert close failure", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of(
                "alerts-and-schedulers-closed",
                "other-resources-closed",
                "workers-drained",
                "mariadb-closed"
        ), events);
    }

}
