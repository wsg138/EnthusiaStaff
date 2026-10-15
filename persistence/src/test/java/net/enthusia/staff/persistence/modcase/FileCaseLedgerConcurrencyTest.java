package net.enthusia.staff.persistence.modcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Single-JVM ledger stress tests using synthetic, content-free notices.
 * These tests do not imply distributed locking, canonical API command idempotency,
 * or an enabled production staff control route.
 */
class FileCaseLedgerConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
    private static final String SCOPE = "synthetic/server:review";

    @TempDir Path directory;

    private static CaseNotice notice(UUID id, long revision, CaseNotice.State state) {
        String claimant = state == CaseNotice.State.OPEN ? null : "staff:synthetic";
        return new CaseNotice(id, CaseNotice.Origin.MINECRAFT, SCOPE, revision,
                CaseNotice.Priority.HIGH, state, List.of("event:synthetic:1"),
                NOW.minusSeconds(5), NOW.plusSeconds(900), claimant);
    }

    private static CaseDelivery.Key key(CaseNotice notice, CaseDelivery.Destination destination) {
        return new CaseDelivery.Key(notice.origin(), notice.scope(), notice.caseId(),
                notice.revision(), destination);
    }

    private static <T> List<T> together(List<Callable<T>> operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> results = new ArrayList<>();
            for (Callable<T> operation : operations) {
                results.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("concurrency start timeout");
                    }
                    return operation.call();
                }));
            }
            start.countDown();
            List<T> values = new ArrayList<>();
            for (Future<T> future : results) {
                values.add(future.get(15, TimeUnit.SECONDS));
            }
            return values;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousIdenticalCanonicalEventsCommitOnlyOnce() throws Exception {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        CaseNotice event = notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN);
        List<CaseLedger.Ingest> outcomes = together(List.of(
                () -> ledger.accept(event), () -> ledger.accept(event)));
        assertEquals(1, outcomes.stream().filter(result -> result == CaseLedger.Ingest.CREATED).count());
        assertEquals(1, outcomes.stream().filter(result -> result == CaseLedger.Ingest.REPLAY).count());
        assertEquals(2, ledger.unresolved().size());
        assertEquals(event, new FileCaseLedger(directory)
                .find(key(event, CaseDelivery.Destination.DISCORD)).orElseThrow());
    }

    @Test
    void racingCanonicalRevisionsNeverRollProjectionBackward() throws Exception {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice first = notice(id, 1, CaseNotice.State.OPEN);
        CaseNotice second = notice(id, 2, CaseNotice.State.CLAIMED);
        List<CaseLedger.Ingest> outcomes = together(List.of(
                () -> ledger.accept(first), () -> ledger.accept(second)));
        assertTrue(outcomes.contains(CaseLedger.Ingest.CREATED));
        assertTrue(outcomes.contains(CaseLedger.Ingest.ADVANCED)
                || outcomes.contains(CaseLedger.Ingest.STALE));
        assertEquals(second, ledger.find(key(second, CaseDelivery.Destination.MINECRAFT))
                .orElseThrow());
        assertEquals(CaseLedger.Ingest.STALE, ledger.accept(first));
        assertEquals(second, new FileCaseLedger(directory)
                .find(key(second, CaseDelivery.Destination.DISCORD)).orElseThrow());
    }

    @Test
    void concurrentCanonicalResolutionAndClaimReplayStayMonotonic() throws Exception {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice claimed = notice(id, 2, CaseNotice.State.CLAIMED);
        CaseNotice resolved = notice(id, 3, CaseNotice.State.RESOLVED);
        assertEquals(CaseLedger.Ingest.CREATED, ledger.accept(claimed));
        List<CaseLedger.Ingest> results = together(List.of(
                () -> ledger.accept(claimed),
                () -> ledger.accept(resolved),
                () -> ledger.accept(claimed)));
        assertTrue(results.contains(CaseLedger.Ingest.ADVANCED));
        assertTrue(results.stream().allMatch(result ->
                result == CaseLedger.Ingest.ADVANCED
                        || result == CaseLedger.Ingest.REPLAY
                        || result == CaseLedger.Ingest.STALE));
        assertEquals(resolved, ledger.find(key(resolved, CaseDelivery.Destination.DISCORD))
                .orElseThrow());
        assertTrue(ledger.active(NOW, 10).isEmpty());
        assertEquals(CaseLedger.Ingest.REPLAY, ledger.accept(resolved));
    }

    @Test
    void competingBeginsUseOneDurableLeaseAndSeparateSurfaceReceipts() throws Exception {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        CaseNotice event = notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN);
        ledger.accept(event);
        CaseDelivery.Key discord = key(event, CaseDelivery.Destination.DISCORD);
        CaseDelivery.Key paper = key(event, CaseDelivery.Destination.MINECRAFT);
        List<java.util.Optional<CaseDelivery>> discordStarts = together(List.of(
                () -> ledger.begin(discord, NOW), () -> ledger.begin(discord, NOW)));
        assertEquals(1, discordStarts.stream().filter(java.util.Optional::isPresent).count());
        CaseDelivery discordLease = discordStarts.stream().flatMap(java.util.Optional::stream)
                .findFirst().orElseThrow();
        CaseDelivery paperLease = ledger.begin(paper, NOW).orElseThrow();
        List<Boolean> acknowledgements = together(List.of(
                () -> ledger.acknowledge(discord, discordLease.attemptToken(), "discord:synthetic:1"),
                () -> ledger.acknowledge(paper, paperLease.attemptToken(), "paper:synthetic:1")));
        assertEquals(List.of(true, true), acknowledgements);
        assertFalse(ledger.acknowledge(discord, discordLease.attemptToken(), "discord:duplicate"));
        assertFalse(ledger.acknowledge(paper, discordLease.attemptToken(), "paper:cross-token"));
        assertTrue(new FileCaseLedger(directory).unresolved().isEmpty());
    }

    @Test
    void restartFencesAmbiguousDeliveryUntilExplicitAbsenceProof() {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice first = notice(id, 1, CaseNotice.State.OPEN);
        ledger.accept(first);
        CaseDelivery.Key discordOld = key(first, CaseDelivery.Destination.DISCORD);
        CaseDelivery.Key paperOld = key(first, CaseDelivery.Destination.MINECRAFT);
        CaseDelivery discordLease = ledger.begin(discordOld, NOW).orElseThrow();
        CaseDelivery paperLease = ledger.begin(paperOld, NOW).orElseThrow();
        CaseNotice second = notice(id, 2, CaseNotice.State.CLAIMED);
        ledger.accept(second);
        FileCaseLedger restarted = new FileCaseLedger(directory);
        assertEquals(2, restarted.unresolved().stream()
                .filter(delivery -> delivery.state() == CaseDelivery.State.UNCERTAIN).count());
        assertTrue(restarted.due(NOW.plusSeconds(10), 10).isEmpty());
        assertFalse(restarted.acknowledge(discordOld, discordLease.attemptToken(), "discord:late"));
        assertFalse(restarted.acknowledge(paperOld, paperLease.attemptToken(), "paper:late"));
        assertFalse(restarted.reconcile(discordOld, null, false, NOW));
        assertTrue(restarted.reconcile(discordOld, "discord:recovered", false, NOW));
        assertEquals("discord:recovered", restarted.previousReceipt(
                key(second, CaseDelivery.Destination.DISCORD)).orElseThrow());
        assertEquals(1, restarted.due(NOW.plusSeconds(10), 10).size());
        assertEquals(CaseDelivery.Destination.DISCORD,
                restarted.due(NOW.plusSeconds(10), 10).getFirst().key().destination());
        assertTrue(restarted.reconcile(paperOld, null, true, NOW.plusSeconds(15)));
        assertEquals(2, new FileCaseLedger(directory).due(NOW.plusSeconds(20), 10).size());
    }

    @Test
    void reentrantAuthorizationCannotOverwriteNewerCanonicalRevision() {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice first = notice(id, 1, CaseNotice.State.OPEN);
        CaseNotice canonicalClaim = notice(id, 2, CaseNotice.State.CLAIMED);
        assertEquals(CaseLedger.Ingest.CREATED, ledger.accept(first));
        CaseLedger.Change action = ledger.action(key(first, CaseDelivery.Destination.DISCORD),
                1, "staff:synthetic", CaseNotice.Action.CLAIM, NOW,
                (actor, current, operation) -> {
                    assertEquals(CaseLedger.Ingest.ADVANCED, ledger.accept(canonicalClaim));
                    return true;
                });
        assertEquals(CaseLedger.Change.STALE, action);
        assertEquals(canonicalClaim, ledger.find(
                key(canonicalClaim, CaseDelivery.Destination.DISCORD)).orElseThrow());
        assertEquals(CaseLedger.Ingest.REPLAY, ledger.accept(canonicalClaim));
    }

    @Test
    void orphanedTemporaryFileDoesNotReplaceCommittedJournal() throws Exception {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        CaseNotice event = notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN);
        ledger.accept(event);
        java.nio.file.Files.writeString(directory.resolve(".moderation-case-synthetic.pending"),
                "{uncommitted}");
        FileCaseLedger restored = new FileCaseLedger(directory);
        assertEquals(event, restored.find(key(event, CaseDelivery.Destination.DISCORD))
                .orElseThrow());
    }

    @Test
    void corruptJournalCannotSilentlyResetCaseState() throws Exception {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        ledger.accept(notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN));
        java.nio.file.Files.writeString(directory.resolve("moderation-case-ledger.json"),
                "{invalid snapshot}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new FileCaseLedger(directory));
    }

    @Test
    void canonicalReopenAfterRestartRestoresActiveInbox() {
        FileCaseLedger ledger = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice resolved = notice(id, 3, CaseNotice.State.RESOLVED);
        assertEquals(CaseLedger.Ingest.CREATED, ledger.accept(resolved));
        assertTrue(ledger.active(NOW, 10).isEmpty());
        FileCaseLedger recovered = new FileCaseLedger(directory);
        CaseNotice reopened = notice(id, 4, CaseNotice.State.OPEN);
        assertEquals(CaseLedger.Ingest.ADVANCED, recovered.accept(reopened));
        FileCaseLedger restartedAgain = new FileCaseLedger(directory);
        assertEquals(List.of(reopened), restartedAgain.active(NOW, 10));
        assertEquals(CaseLedger.Ingest.STALE, restartedAgain.accept(resolved));
        assertEquals(2, restartedAgain.due(NOW, 10).size());
    }
}
