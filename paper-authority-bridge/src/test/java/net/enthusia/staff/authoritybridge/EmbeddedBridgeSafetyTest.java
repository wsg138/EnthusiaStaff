package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.TransitionDataRuntime;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddedBridgeSafetyTest {
    @TempDir
    Path directory;

    @Test
    void embeddedSwitchesAreOffByDefaultAndExplicitlyValidated() throws IOException {
        assertFalse(EmbeddedAuthorityBridge.requested(directory));
        Path config = directory.resolve(EmbeddedAuthorityBridge.SWITCH_FILE);
        Files.writeString(config, "authority.enabled=false\ncollector.enabled=false\n");
        assertFalse(EmbeddedAuthorityBridge.requested(directory));
        Files.writeString(config, "authority.enabled=true\ncollector.enabled=false\n");
        assertTrue(EmbeddedAuthorityBridge.requested(directory));
        Files.writeString(config, "authority.enabled=maybe\n");
        assertThrows(IllegalArgumentException.class, () -> EmbeddedAuthorityBridge.requested(directory));
        Files.writeString(config, "unknown.enabled=true\n");
        assertThrows(IllegalArgumentException.class, () -> EmbeddedAuthorityBridge.requested(directory));
    }

    @Test
    void offlineImportPlannerCapsLookupCandidatesAndAdvancesDeterministically() {
        Map<String, UUID> links = new LinkedHashMap<>();
        for (int i = 0; i < 5_000; i++) {
            links.put(Integer.toString(i + 100_000), fixturePlayer(i + 1));
        }
        TransitionSnapshotPlanner.LinkBatch first =
                TransitionSnapshotPlanner.selectLinkBatch(links, 0, 128, Set.of());
        TransitionSnapshotPlanner.LinkBatch second =
                TransitionSnapshotPlanner.selectLinkBatch(links, first.nextCursor(), 128, Set.of());
        assertEquals(128, first.links().size());
        assertEquals(128, second.links().size());
        assertTrue(first.links().values().stream().noneMatch(second.links().values()::contains));
        assertEquals(128, first.nextCursor());
        assertEquals(256, second.nextCursor());
        assertEquals(first, TransitionSnapshotPlanner.selectLinkBatch(links, 0, 128, Set.of()));
    }

    @Test
    void alwaysIncludesCurrentOnlineIdentityOutsideOfflineBatch() {
        UUID known = new UUID(0L, 5_000);
        Map<String, UUID> links = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) {
            links.put(Integer.toString(i), fixturePlayer(i + 1));
        }
        links.put("current", known);
        TransitionSnapshotPlanner.LinkBatch batch =
                TransitionSnapshotPlanner.selectLinkBatch(links, 0, 3, Set.of(known));
        assertEquals(4, batch.links().size());
        assertTrue(batch.links().containsKey("current"));
        assertEquals(3, batch.nextCursor());
    }

    @Test
    void borrowedPaperConnectionPoolIsNotClosedByCollectorAdapter() {
        DataSource primaryPool = (DataSource) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    throw new AssertionError("borrowed pool must not be called during open or close");
                });
        assertDoesNotThrow(() -> TransitionDataRuntime.borrow(primaryPool).close());
    }

    @Test
    void embeddedCollectorRejectsDifferentDatabaseWithoutLeakingCredentials() {
        DatabaseConfig primary = new DatabaseConfig("jdbc:mariadb://localhost:3306/staff", "staff",
                "private-one", 4, 3_000);
        DatabaseConfig sameWithDifferentPool = new DatabaseConfig("jdbc:mariadb://localhost:3306/staff",
                "staff", "private-one", 2, 5_000);
        DatabaseConfig wrong = new DatabaseConfig("jdbc:mariadb://localhost:3306/staff", "staff",
                "private-two", 4, 3_000);
        EmbeddedAuthorityBridge.verifySameDatabase(sameWithDifferentPool, primary);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> EmbeddedAuthorityBridge.verifySameDatabase(wrong, primary));
        assertFalse(exception.getMessage().contains("private"));
    }

    @Test
    void routineCollectorHasNoBukkitOfflinePlayerDiskLookup() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/authoritybridge/TransitionDataCollector.java"));
        assertFalse(source.contains("getOfflinePlayer("));
        assertFalse(source.contains("getLastSeen("));
    }

    @Test
    void quitEventWinsOverRosterUntilNewJoinIsObserved() {
        UUID id = fixturePlayer(71);
        Instant now = Instant.parse("2026-10-11T14:00:00Z");
        TransitionSnapshotPlanner.Observation online =
                new TransitionSnapshotPlanner.Observation(id, "Active", now, true);
        TransitionSnapshotPlanner.Observation quit =
                new TransitionSnapshotPlanner.Observation(id, "Active", now.plusSeconds(1), false);
        TransitionSnapshotPlanner.Observation rejoin =
                new TransitionSnapshotPlanner.Observation(id, "Active", now.plusSeconds(2), true);

        assertEquals(quit, TransitionSnapshotPlanner.mergePending(
                Map.of(id, online), Map.of(id, quit)).get(id));
        assertEquals(rejoin, TransitionSnapshotPlanner.mergePending(
                Map.of(id, online), Map.of(id, rejoin)).get(id));
        assertEquals(online, TransitionSnapshotPlanner.mergePending(
                Map.of(id, online), Map.of()).get(id));
    }

    @Test
    void acknowledgedObservationRetiresWithoutDiscardingNewerConcurrentEvent() {
        UUID id = fixturePlayer(19);
        TransitionSnapshotPlanner.Observation first = new TransitionSnapshotPlanner.Observation(
                id, "First", Instant.parse("2026-10-11T14:00:00Z"), true);
        TransitionSnapshotPlanner.Observation newer = new TransitionSnapshotPlanner.Observation(
                id, "First", Instant.parse("2026-10-11T14:01:00Z"), false);
        ConcurrentMap<UUID, TransitionSnapshotPlanner.Observation> pending = new ConcurrentHashMap<>();
        pending.put(id, first);
        TransitionDataCollector.retireAcknowledged(pending, Map.of(id, first));
        assertTrue(pending.isEmpty());

        pending.put(id, first);
        pending.replace(id, newer);
        TransitionDataCollector.retireAcknowledged(pending, Map.of(id, first));
        assertEquals(newer, pending.get(id));

        TransitionDataCollector.retireAcknowledged(pending, Map.of(id, newer));
        assertTrue(pending.isEmpty());
    }

    private static UUID fixturePlayer(long id) {
        return new UUID(0L, id);
    }

    @Test
    void cursorSurvivesLinkMembershipChangesAndEmptySnapshots() {
        Map<String, UUID> links = Map.of(
                "a", new UUID(0, 1), "b", new UUID(0, 2), "c", new UUID(0, 3));
        assertEquals(2, TransitionSnapshotPlanner.selectLinkBatch(links, 3_004, 1, Set.of()).nextCursor());
        assertEquals(0, TransitionSnapshotPlanner.selectLinkBatch(Map.of(), 3_004, 128, Set.of()).nextCursor());
        assertThrows(IllegalArgumentException.class, () ->
                TransitionSnapshotPlanner.selectLinkBatch(links, -1, 128, Set.of()));
    }
}
