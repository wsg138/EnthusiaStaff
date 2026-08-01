package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.enthusia.staff.domain.OperationalMode;
import org.junit.jupiter.api.Test;

class VelocityRuntimeHealthTest {
    @Test
    void startsInBootstrapWithAnInitializationIssue() {
        VelocityRuntimeHealth.Snapshot snapshot = new VelocityRuntimeHealth().snapshot();

        assertEquals(OperationalMode.BOOTSTRAP, snapshot.mode());
        assertEquals("Initialization has not completed", snapshot.issues().get("bootstrap"));
    }

    @Test
    void updateDefensivelyCopiesAndFreezesIssues() {
        VelocityRuntimeHealth health = new VelocityRuntimeHealth();
        Map<String, String> issues = new HashMap<>();
        issues.put("channel", "offline");

        health.update(OperationalMode.DEGRADED, issues);
        issues.clear();

        VelocityRuntimeHealth.Snapshot snapshot = health.snapshot();
        assertEquals(Map.of("channel", "offline"), snapshot.issues());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.issues().put("other", "value"));
    }

    @Test
    void updateReplacesTheWholeSnapshotWithoutMutatingTheOldOne() {
        VelocityRuntimeHealth health = new VelocityRuntimeHealth();
        health.update(OperationalMode.DEGRADED, Map.of("channel", "offline"));
        VelocityRuntimeHealth.Snapshot degraded = health.snapshot();

        health.update(OperationalMode.ACTIVE, Map.of());

        assertNotSame(degraded, health.snapshot());
        assertEquals(OperationalMode.DEGRADED, degraded.mode());
        assertEquals(Map.of("channel", "offline"), degraded.issues());
        assertEquals(OperationalMode.ACTIVE, health.snapshot().mode());
        assertTrue(health.snapshot().issues().isEmpty());
    }
}
