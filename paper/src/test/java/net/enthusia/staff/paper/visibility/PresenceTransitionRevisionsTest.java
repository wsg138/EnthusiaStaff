package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class PresenceTransitionRevisionsTest {
    private static final UUID SUBJECT = UUID.fromString("fd07dc16-1666-4c7d-9b28-aa10090a621a");
    private static final UUID OTHER = UUID.fromString("0c85771b-189e-4d13-8e1d-a6310099611e");

    @Test
    void queuedCallbacksCannotReplayAnOlderQuitAfterEnableDisableEnable() {
        PresenceTransitionRevisions revisions = new PresenceTransitionRevisions();
        AtomicInteger notices = new AtomicInteger();
        List<Runnable> delayed = new ArrayList<>();

        UUID firstEnable = revisions.advance(SUBJECT);
        delayed.add(() -> notifyIfCurrent(revisions, firstEnable, notices));
        UUID disable = revisions.advance(SUBJECT);
        delayed.add(() -> notifyIfCurrent(revisions, disable, notices));
        UUID secondEnable = revisions.advance(SUBJECT);
        delayed.add(() -> notifyIfCurrent(revisions, secondEnable, notices));

        delayed.forEach(Runnable::run);
        assertEquals(1, notices.get());
        assertFalse(revisions.isCurrent(SUBJECT, firstEnable));
        assertFalse(revisions.isCurrent(SUBJECT, disable));
        assertTrue(revisions.isCurrent(SUBJECT, secondEnable));
    }

    @Test
    void disconnectAndReconnectCannotReanimateOldCallbacks() {
        PresenceTransitionRevisions revisions = new PresenceTransitionRevisions();
        UUID beforeQuit = revisions.advance(SUBJECT);
        revisions.retire(SUBJECT);
        assertFalse(revisions.isCurrent(SUBJECT, beforeQuit));
        UUID afterJoin = revisions.advance(SUBJECT);
        assertFalse(revisions.isCurrent(SUBJECT, beforeQuit));
        assertTrue(revisions.isCurrent(SUBJECT, afterJoin));
    }

    @Test
    void staleRetirementCannotEraseNewerCommitAndSubjectsStayIndependent() {
        PresenceTransitionRevisions revisions = new PresenceTransitionRevisions();
        UUID first = revisions.advance(SUBJECT);
        UUID other = revisions.advance(OTHER);
        UUID second = revisions.advance(SUBJECT);
        revisions.retire(SUBJECT, first);
        assertTrue(revisions.isCurrent(SUBJECT, second));
        assertTrue(revisions.isCurrent(OTHER, other));
        revisions.retire(SUBJECT, second);
        assertFalse(revisions.isCurrent(SUBJECT, second));
        assertTrue(revisions.isCurrent(OTHER, other));
    }

    private static void notifyIfCurrent(
            PresenceTransitionRevisions revisions, UUID revision, AtomicInteger notices
    ) {
        if (revisions.isCurrent(SUBJECT, revision)) {
            notices.incrementAndGet();
        }
    }
}
