package net.enthusia.staff.persistence.modcase;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthorityBackedCaseActionsTest {
    private static final Instant NOW = Instant.parse("2026-10-14T16:00:00Z");
    @TempDir Path directory;

    private CaseNotice notice(UUID id, long version, CaseNotice.State state, String claimant) {
        return new CaseNotice(id, CaseNotice.Origin.MINECRAFT, "smp",
                version, CaseNotice.Priority.URGENT, state, List.of("evt:first"),
                NOW.minusSeconds(3), NOW.plusSeconds(120), claimant);
    }

    private CaseDelivery.Key key(CaseNotice event) {
        return new CaseDelivery.Key(event.origin(), event.scope(), event.caseId(),
                event.revision(), CaseDelivery.Destination.DISCORD);
    }

    private AuthorityBackedCaseActions actions(FileCaseLedger replica,
            AuthorityBackedCaseActions.Gateway gateway, CaseLedger.StaffGate gate) {
        return new AuthorityBackedCaseActions(replica, gate,
                Clock.fixed(NOW, ZoneOffset.UTC), gateway);
    }

    @Test
    void acceptedClaimNeverWritesLocalReplicaUntilAuthoritativeEventArrives() {
        FileCaseLedger replica = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice open = notice(id, 1, CaseNotice.State.OPEN, null);
        assertEquals(CaseLedger.Ingest.CREATED, replica.accept(open));
        List<AuthorityBackedCaseActions.Command> sent = new ArrayList<>();
        AuthorityBackedCaseActions actions = actions(replica, command -> {
            sent.add(command);
            return AuthorityBackedCaseActions.TransportStatus.ACCEPTED;
        }, (actor, event, action) -> actor.equals("staff:alice"));
        assertEquals(AuthorityBackedCaseActions.Outcome.SUBMITTED_AWAITING_EVENT,
                actions.claim(key(open), 1, "staff:alice", "command:claim:1"));
        assertEquals(1, sent.size());
        assertEquals(1, sent.get(0).expectedRevision());
        assertEquals(id, sent.get(0).caseId());
        assertEquals(CaseNotice.State.OPEN, replica.find(key(open)).orElseThrow().state());
        assertEquals(1, replica.find(key(open)).orElseThrow().revision());
        CaseNotice claimed = notice(id, 2, CaseNotice.State.CLAIMED, "staff:alice");
        assertEquals(CaseLedger.Ingest.ADVANCED, replica.accept(claimed));
        assertEquals(AuthorityBackedCaseActions.Outcome.SUBMITTED_AWAITING_EVENT,
                actions.resolve(key(claimed), 2, "staff:alice", "command:resolve:2"));
        assertEquals(CaseNotice.State.CLAIMED,
                replica.find(key(claimed)).orElseThrow().state());
        assertEquals(2, replica.find(key(claimed)).orElseThrow().revision());
    }

    @Test
    void staleDeniedAndCrossActorResolveNeverReachRemoteGateway() {
        FileCaseLedger replica = new FileCaseLedger(directory);
        CaseNotice claimed = notice(UUID.randomUUID(), 5,
                CaseNotice.State.CLAIMED, "staff:alice");
        replica.accept(claimed);
        List<AuthorityBackedCaseActions.Command> sent = new ArrayList<>();
        AuthorityBackedCaseActions actions = actions(replica, cmd -> {
            sent.add(cmd);
            return AuthorityBackedCaseActions.TransportStatus.ACCEPTED;
        }, (actor, event, kind) -> actor.startsWith("staff:"));
        assertEquals(AuthorityBackedCaseActions.Outcome.STALE,
                actions.resolve(key(claimed), 4, "staff:alice", "command:stale:01"));
        assertEquals(AuthorityBackedCaseActions.Outcome.DENIED,
                actions.resolve(key(claimed), 5, "visitor", "command:denied:1"));
        assertEquals(AuthorityBackedCaseActions.Outcome.NOT_APPLICABLE,
                actions.resolve(key(claimed), 5, "staff:bob", "command:other:01"));
        assertEquals(AuthorityBackedCaseActions.Outcome.NOT_APPLICABLE,
                actions.claim(key(claimed), 5, "staff:alice", "command:claim:05"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void transportAcknowledgmentIsNotLocalCaseResolution() {
        FileCaseLedger replica = new FileCaseLedger(directory);
        CaseNotice open = notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN, null);
        replica.accept(open);
        AuthorityBackedCaseActions pending = actions(replica,
                cmd -> AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                (actor, event, action) -> true);
        assertEquals(AuthorityBackedCaseActions.Outcome.RETRYABLE,
                pending.claim(key(open), 1, "staff:alice", "command:retry:1"));
        assertEquals(CaseNotice.State.OPEN, replica.find(key(open)).orElseThrow().state());
        AuthorityBackedCaseActions stale = actions(replica,
                cmd -> AuthorityBackedCaseActions.TransportStatus.STALE,
                (actor, event, action) -> true);
        assertEquals(AuthorityBackedCaseActions.Outcome.STALE,
                stale.claim(key(open), 1, "staff:alice", "command:stale:2"));
        assertEquals(CaseNotice.State.OPEN, replica.find(key(open)).orElseThrow().state());
    }

    @Test
    void malformedIdempotencyKeyFailsClosedBeforeNetwork() {
        FileCaseLedger replica = new FileCaseLedger(directory);
        CaseNotice open = notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN, null);
        replica.accept(open);
        List<AuthorityBackedCaseActions.Command> sent = new ArrayList<>();
        AuthorityBackedCaseActions actions = actions(replica, command -> {
            sent.add(command);
            return AuthorityBackedCaseActions.TransportStatus.ACCEPTED;
        }, (actor, event, action) -> true);
        assertThrows(IllegalArgumentException.class,
                () -> actions.claim(key(open), 1, "staff:alice", "x"));
        assertTrue(sent.isEmpty());
        assertEquals(CaseNotice.State.OPEN, replica.find(key(open)).orElseThrow().state());
    }

    @Test
    void mismatchedOriginOrScopeCannotSubmitEvenWithSameId() {
        FileCaseLedger replica = new FileCaseLedger(directory);
        CaseNotice open = notice(UUID.randomUUID(), 1, CaseNotice.State.OPEN, null);
        replica.accept(open);
        CaseDelivery.Key crossScope = new CaseDelivery.Key(
                open.origin(), "different-server", open.caseId(), 1,
                CaseDelivery.Destination.MINECRAFT);
        AuthorityBackedCaseActions actions = actions(replica,
                cmd -> fail("Must not submit cross-scope command"),
                (actor, event, action) -> true);
        assertEquals(AuthorityBackedCaseActions.Outcome.NOT_FOUND,
                actions.claim(crossScope, 1, "staff:alice", "command:wrong:01"));
    }

    @Test
    void canonicalRevisionZeroBootstrapAndRemoteClaimArePreserved() {
        FileCaseLedger replica = new FileCaseLedger(directory);
        UUID id = UUID.randomUUID();
        CaseNotice first = notice(id, 0, CaseNotice.State.OPEN, null);
        assertEquals(CaseLedger.Ingest.CREATED, replica.accept(first));
        CaseDelivery.Key zero = key(first);
        assertEquals(0, zero.revision());
        List<AuthorityBackedCaseActions.Command> sent = new ArrayList<>();
        AuthorityBackedCaseActions actions = actions(replica, command -> {
            sent.add(command);
            return AuthorityBackedCaseActions.TransportStatus.ACCEPTED;
        }, (actor, event, action) -> actor.equals("staff:alice"));
        assertEquals(AuthorityBackedCaseActions.Outcome.SUBMITTED_AWAITING_EVENT,
                actions.claim(zero, 0, "staff:alice", "command:claim:zero"));
        assertEquals(0, sent.get(0).expectedRevision());
        assertEquals(0, replica.find(zero).orElseThrow().revision());
        assertEquals(CaseNotice.State.OPEN, replica.find(zero).orElseThrow().state());
        CaseNotice claimed = notice(id, 1, CaseNotice.State.CLAIMED, "staff:alice");
        assertEquals(CaseLedger.Ingest.ADVANCED, replica.accept(claimed));
        assertEquals(AuthorityBackedCaseActions.Outcome.SUBMITTED_AWAITING_EVENT,
                actions.resolve(key(claimed), 1, "staff:alice", "command:resolve:01"));
    }

    @Test
    void negativeRevisionsStillRejectedOnAllThreeBoundaries() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> notice(id, -1, CaseNotice.State.OPEN, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CaseDelivery.Key(CaseNotice.Origin.MINECRAFT, "smp", id,
                        -1, CaseDelivery.Destination.DISCORD));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorityBackedCaseActions.Command(CaseNotice.Origin.MINECRAFT,
                        "smp", id, -1, "staff:alice",
                        CaseNotice.Action.CLAIM, "command:invalid:1"));
    }

}
