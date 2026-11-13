package net.enthusia.staff.persistence.modcase;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseDispatchWorker;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;
import net.enthusia.staff.domain.modcase.CaseStaffActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCaseLedgerTest {
    private static final Instant NOW = Instant.parse("2026-10-14T16:00:00Z");
    @TempDir Path temporary;

    private CaseNotice event(CaseNotice.Origin origin, String scope, UUID id, long revision) {
        return new CaseNotice(id, origin, scope, revision, CaseNotice.Priority.HIGH,
                CaseNotice.State.OPEN, List.of("evt:1", "evt:2"), NOW.minusSeconds(3),
                NOW.plusSeconds(600), null);
    }

    private CaseDelivery.Key key(CaseNotice notice, CaseDelivery.Destination destination) {
        return new CaseDelivery.Key(notice.origin(), notice.scope(), notice.caseId(),
                notice.revision(), destination);
    }

    @Test
    void sameCaseRevisionReplaysButConflictingAndStaleUpdatesDoNotOverwrite() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        UUID id = UUID.randomUUID();
        CaseNotice first = event(CaseNotice.Origin.MINECRAFT, "smp/chat", id, 1);
        assertEquals(CaseLedger.Ingest.CREATED, store.accept(first));
        assertEquals(CaseLedger.Ingest.REPLAY, store.accept(first));
        assertEquals(CaseLedger.Ingest.CONFLICT, store.accept(new CaseNotice(id, first.origin(),
                first.scope(), 1, CaseNotice.Priority.URGENT, first.state(),
                first.evidenceRefs(), first.createdAt(), first.expiresAt(), null)));
        CaseNotice second = event(first.origin(), first.scope(), id, 2);
        assertEquals(CaseLedger.Ingest.ADVANCED, store.accept(second));
        assertEquals(CaseLedger.Ingest.STALE, store.accept(first));
        assertEquals(2, store.due(NOW, 100).size());
        assertEquals(4, store.unresolved().size());
    }

    @Test
    void twoDestinationReceiptsRemainIndependentAfterRestart() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice notice = event(CaseNotice.Origin.DISCORD, "guild:22/channel:3", UUID.randomUUID(), 1);
        store.accept(notice);
        CaseDelivery.Key discord = key(notice, CaseDelivery.Destination.DISCORD);
        CaseDelivery.Key paper = key(notice, CaseDelivery.Destination.MINECRAFT);
        CaseDelivery first = store.begin(discord, NOW).orElseThrow();
        assertTrue(store.acknowledge(discord, first.attemptToken(), "discord:posted:111"));
        assertFalse(store.acknowledge(discord, first.attemptToken(), "discord:posted:222"));
        CaseDelivery other = store.begin(paper, NOW).orElseThrow();
        assertTrue(store.retry(paper, other.attemptToken(), "STAFF_OFFLINE", NOW.plusSeconds(5)));
        FileCaseLedger recovered = new FileCaseLedger(temporary);
        assertEquals(1, recovered.unresolved().size());
        assertEquals(0, recovered.due(NOW, 10).size());
        assertEquals(1, recovered.due(NOW.plusSeconds(6), 10).size());
    }

    @Test
    void inFlightCrashBecomesUncertainAndCannotDuplicatePing() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice notice = event(CaseNotice.Origin.MINECRAFT, "smp/chat", UUID.randomUUID(), 1);
        store.accept(notice);
        CaseDelivery.Key key = key(notice, CaseDelivery.Destination.DISCORD);
        CaseDelivery claimed = store.begin(key, NOW).orElseThrow();
        FileCaseLedger restarted = new FileCaseLedger(temporary);
        assertEquals(CaseDelivery.State.UNCERTAIN, restarted.unresolved().stream()
                .filter(d -> d.key().equals(key)).findFirst().orElseThrow().state());
        assertEquals(1, restarted.due(NOW.plusSeconds(120), 5).size()); // other destination only
        assertFalse(restarted.acknowledge(key, claimed.attemptToken(), "111"));
        assertFalse(restarted.reconcile(key, null, false, NOW));
        assertTrue(restarted.reconcile(key, "discord:111", false, NOW));
        assertEquals(CaseDelivery.State.DELIVERED, new FileCaseLedger(temporary).unresolved().stream()
                .filter(d -> d.key().equals(key)).findFirst().map(CaseDelivery::state)
                .orElse(CaseDelivery.State.DELIVERED));
    }

    @Test
    void verifiedAbsentCanRetryButUnverifiedOutcomeCannot() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice notice = event(CaseNotice.Origin.DISCORD, "guild:2", UUID.randomUUID(), 1);
        store.accept(notice);
        CaseDelivery.Key key = key(notice, CaseDelivery.Destination.DISCORD);
        CaseDelivery lease = store.begin(key, NOW).orElseThrow();
        assertTrue(store.uncertain(key, lease.attemptToken(), "DELIVERY_UNCONFIRMED"));
        assertFalse(store.reconcile(key, null, false, NOW));
        assertTrue(store.reconcile(key, null, true, NOW.plusSeconds(2)));
        assertTrue(store.due(NOW.plusSeconds(3), 5).stream().anyMatch(d -> d.key().equals(key)));
    }

    @Test
    void scopeIsolationAndFreshAuthorizationProtectClaims() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        UUID id = UUID.randomUUID();
        CaseNotice notice = event(CaseNotice.Origin.MINECRAFT, "smp/chat", id, 1);
        store.accept(notice);
        CaseDelivery.Key key = key(notice, CaseDelivery.Destination.DISCORD);
        CaseDelivery.Key forged = new CaseDelivery.Key(notice.origin(), "other/chat", id,
                1, CaseDelivery.Destination.DISCORD);
        CaseStaffActions denied = new CaseStaffActions(store, (actor, current, action) -> false,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(CaseLedger.Change.DENIED, denied.claim(key, 1, "staff:1"));
        assertEquals(CaseLedger.Change.NOT_FOUND, denied.claim(forged, 1, "staff:1"));
        CaseStaffActions allowed = new CaseStaffActions(store,
                (actor, current, action) -> actor.equals("staff:1"), Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(CaseLedger.Change.APPLIED, allowed.claim(key, 1, "staff:1"));
        assertEquals(CaseLedger.Change.STALE, allowed.claim(key, 1, "staff:1"));
        CaseDelivery.Key rev2 = new CaseDelivery.Key(notice.origin(), notice.scope(), id,
                2, CaseDelivery.Destination.MINECRAFT);
        assertEquals(CaseLedger.Change.DENIED, denied.resolve(rev2, 2, "staff:1"));
        CaseStaffActions otherAuthorized = new CaseStaffActions(store,
                (actor, current, action) -> true, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(CaseLedger.Change.NOT_APPLICABLE, otherAuthorized.resolve(rev2, 2, "staff:2"));
        assertEquals(CaseLedger.Change.APPLIED, allowed.resolve(rev2, 2, "staff:1"));
        assertEquals(CaseNotice.State.RESOLVED, store.find(rev2).orElseThrow().state());
    }

    @Test
    void concurrentClaimsSelectExactlyOneWinner() throws InterruptedException {
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice notice = event(CaseNotice.Origin.MINECRAFT, "smp", UUID.randomUUID(), 1);
        store.accept(notice);
        CaseDelivery.Key key = key(notice, CaseDelivery.Destination.MINECRAFT);
        CaseStaffActions actions = new CaseStaffActions(store,
                (actor, current, action) -> true, Clock.fixed(NOW, ZoneOffset.UTC));
        AtomicInteger winners = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Runnable contender = () -> {
            try {
                start.await();
                if (actions.claim(key, 1, Thread.currentThread().getName()) == CaseLedger.Change.APPLIED) {
                    winners.incrementAndGet();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        Thread first = new Thread(contender, "staffA");
        Thread second = new Thread(contender, "staffB");
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();
        assertEquals(1, winners.get());
    }

    @Test
    void asynchronousTwoEndpointDispatchDoesNotAcknowledgeUnknownSends() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice notice = event(CaseNotice.Origin.MINECRAFT, "smp", UUID.randomUUID(), 1);
        store.accept(notice);
        Map<CaseDelivery.Destination, CaseDispatchWorker.Destination> adapters =
                new EnumMap<>(CaseDelivery.Destination.class);
        CompletableFuture<CaseDispatchWorker.Result> discord = new CompletableFuture<>();
        adapters.put(CaseDelivery.Destination.DISCORD, (snapshot, key) -> discord);
        adapters.put(CaseDelivery.Destination.MINECRAFT, (snapshot, key) ->
                CompletableFuture.completedFuture(CaseDispatchWorker.Result.notSent("STAFF_OFFLINE")));
        CaseDispatchWorker worker = new CaseDispatchWorker(store, adapters,
                Clock.fixed(NOW, ZoneOffset.UTC));
        worker.runCycle(10);
        assertEquals(2, store.unresolved().size());
        discord.complete(CaseDispatchWorker.Result.possiblySent("TIMEOUT"));
        assertEquals(1, store.unresolved().stream()
                .filter(d -> d.state() == CaseDelivery.State.UNCERTAIN).count());
        worker.runCycle(10);
        assertEquals(2, store.unresolved().size());
    }

    @Test
    void uncertainPreviousRevisionFencesNewDiscordCardButNotPaper() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        UUID id = UUID.randomUUID();
        CaseNotice first = event(CaseNotice.Origin.DISCORD, "guild:2", id, 1);
        store.accept(first);
        CaseDelivery.Key original = key(first, CaseDelivery.Destination.DISCORD);
        store.begin(original, NOW).orElseThrow();
        CaseNotice newer = event(first.origin(), first.scope(), id, 2);
        store.accept(newer);
        assertEquals(1, store.due(NOW, 10).size());
        assertEquals(CaseDelivery.Destination.MINECRAFT, store.due(NOW, 10).getFirst().key().destination());
        FileCaseLedger recovered = new FileCaseLedger(temporary);
        assertEquals(1, recovered.due(NOW, 10).size());
        assertTrue(recovered.reconcile(original, "discord:previous:123", false, NOW));
        assertEquals("discord:previous:123",
                recovered.previousReceipt(key(newer, CaseDelivery.Destination.DISCORD)).orElseThrow());
        assertEquals(2, recovered.due(NOW, 10).size());
    }

    @Test
    void ingestRequiresExactAuthenticatedSourceAndScope() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        var ingress = new net.enthusia.staff.domain.modcase.AuthenticatedCaseIngress(store,
                (peer, origin, scope) -> "trusted-api".equals(peer)
                        && origin == CaseNotice.Origin.MINECRAFT && "smp/chat".equals(scope));
        CaseNotice approved = event(CaseNotice.Origin.MINECRAFT, "smp/chat", UUID.randomUUID(), 1);
        assertEquals(net.enthusia.staff.domain.modcase.AuthenticatedCaseIngress.Result.DENIED,
                ingress.ingest("untrusted-api", approved));
        assertEquals(net.enthusia.staff.domain.modcase.AuthenticatedCaseIngress.Result.DENIED,
                ingress.ingest("trusted-api", event(CaseNotice.Origin.DISCORD, "smp/chat",
                        approved.caseId(), 1)));
        assertTrue(store.due(NOW, 10).isEmpty());
        assertEquals(net.enthusia.staff.domain.modcase.AuthenticatedCaseIngress.Result.CREATED,
                ingress.ingest("trusted-api", approved));
    }

    @Test
    void expiredCaseCannotBeClaimedEvenByAuthorizedStaff() {
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice old = new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.MINECRAFT,
                "smp", 1, CaseNotice.Priority.NORMAL, CaseNotice.State.OPEN, List.of("evt:1"),
                NOW.minusSeconds(50), NOW.minusSeconds(10), null);
        store.accept(old);
        CaseStaffActions actions = new CaseStaffActions(store, (actor, snapshot, action) -> true,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(CaseLedger.Change.EXPIRED,
                actions.claim(key(old, CaseDelivery.Destination.MINECRAFT), 1, "staff:1"));
    }

    @Test
    void invalidEvidenceAndExpiredNoticesAreNotDispatched() {
        assertThrows(IllegalArgumentException.class, () -> new CaseNotice(UUID.randomUUID(),
                CaseNotice.Origin.DISCORD, "guild:2", 1, CaseNotice.Priority.URGENT,
                CaseNotice.State.OPEN, List.of("private raw message with spaces"), NOW,
                NOW.plusSeconds(30), null));
        FileCaseLedger store = new FileCaseLedger(temporary);
        CaseNotice notice = new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.MINECRAFT,
                "smp", 1, CaseNotice.Priority.NORMAL, CaseNotice.State.OPEN,
                List.of("evt:1"), NOW.minusSeconds(60), NOW.minusSeconds(30), null);
        store.accept(notice);
        assertTrue(store.due(NOW, 5).isEmpty());
    }
}
