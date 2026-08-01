package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.enthusia.staff.domain.OperationalMode;
import org.junit.jupiter.api.Test;

class RuntimeHealthTest {
    @Test
    void startsInBootstrapWithAnInitializationIssue() {
        RuntimeHealth.Snapshot snapshot = new RuntimeHealth().snapshot();

        assertEquals(OperationalMode.BOOTSTRAP, snapshot.mode());
        assertEquals("Initialization has not completed", snapshot.issues().get("bootstrap"));
        assertFalse(snapshot.issues().isEmpty());
        assertTrue(snapshot.updatedAt() != null);
    }

    @Test
    void updateDefensivelyCopiesTheIssueMap() {
        RuntimeHealth health = new RuntimeHealth();
        Map<String, String> issues = new LinkedHashMap<>();
        issues.put("database", "unavailable");

        health.update(OperationalMode.DEGRADED, issues);
        issues.put("late", "must not leak into the snapshot");

        RuntimeHealth.Snapshot snapshot = health.snapshot();
        assertEquals(Map.of("database", "unavailable"), snapshot.issues());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.issues().put("other", "value"));
    }

    @Test
    void updateReplacesTheWholeSnapshot() {
        RuntimeHealth health = new RuntimeHealth();
        RuntimeHealth.Snapshot initial = health.snapshot();

        health.update(OperationalMode.SHADOW_MIGRATION, Map.of());

        RuntimeHealth.Snapshot updated = health.snapshot();
        assertNotSame(initial, updated);
        assertEquals(OperationalMode.SHADOW_MIGRATION, updated.mode());
        assertTrue(updated.issues().isEmpty());
    }

    @Test
    void aPreviouslyReturnedSnapshotDoesNotChangeAfterLaterUpdates() {
        RuntimeHealth health = new RuntimeHealth();
        health.update(OperationalMode.DEGRADED, Map.of("database", "offline"));
        RuntimeHealth.Snapshot degraded = health.snapshot();

        health.update(OperationalMode.ACTIVE, Map.of());

        assertEquals(OperationalMode.DEGRADED, degraded.mode());
        assertEquals(Map.of("database", "offline"), degraded.issues());
        assertEquals(OperationalMode.ACTIVE, health.snapshot().mode());
    }
}
