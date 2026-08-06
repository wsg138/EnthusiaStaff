package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.enthusia.staff.domain.OperationalMode;
import org.junit.jupiter.api.Test;

class VelocityRuntimeHealthTest {
    private static final String CHANNEL = "channel";
    private static final String OFFLINE = "offline";
    private static final String CONFIGURATION_RELOAD = "configuration-reload";

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
        issues.put(CHANNEL, OFFLINE);

        health.update(OperationalMode.DEGRADED, issues);
        issues.clear();

        VelocityRuntimeHealth.Snapshot snapshot = health.snapshot();
        assertEquals(Map.of(CHANNEL, OFFLINE), snapshot.issues());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.issues().put("other", "value"));
    }

    @Test
    void updateReplacesTheWholeSnapshotWithoutMutatingTheOldOne() {
        VelocityRuntimeHealth health = new VelocityRuntimeHealth();
        health.update(OperationalMode.DEGRADED, Map.of(CHANNEL, OFFLINE));
        VelocityRuntimeHealth.Snapshot degraded = health.snapshot();

        health.update(OperationalMode.ACTIVE, Map.of());

        assertNotSame(degraded, health.snapshot());
        assertEquals(OperationalMode.DEGRADED, degraded.mode());
        assertEquals(Map.of(CHANNEL, OFFLINE), degraded.issues());
        assertEquals(OperationalMode.ACTIVE, health.snapshot().mode());
        assertTrue(health.snapshot().issues().isEmpty());
    }

    @Test
    void issueUpdatesMergeAtomicallyWithoutReplacingCurrentMode() {
        VelocityRuntimeHealth health = new VelocityRuntimeHealth();
        health.update(OperationalMode.DEGRADED, Map.of(CHANNEL, OFFLINE));

        health.updateIssue(CONFIGURATION_RELOAD, "invalid candidate");
        health.updateIssue("mariadb", "refresh failed");
        VelocityRuntimeHealth.Snapshot degraded = health.snapshot();

        assertEquals(OperationalMode.DEGRADED, degraded.mode());
        assertEquals(Map.of(
                CHANNEL, OFFLINE,
                CONFIGURATION_RELOAD, "invalid candidate",
                "mariadb", "refresh failed"
        ), degraded.issues());

        health.updateIssue(CONFIGURATION_RELOAD, null);
        VelocityRuntimeHealth.Snapshot cleared = health.snapshot();
        assertEquals(OperationalMode.DEGRADED, cleared.mode());
        assertFalse(cleared.issues().containsKey(CONFIGURATION_RELOAD));
        assertEquals(OFFLINE, cleared.issues().get(CHANNEL));
        assertEquals("refresh failed", cleared.issues().get("mariadb"));
    }
}
