package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.junit.jupiter.api.Test;

class CheatTesterSessionTest {
    @Test
    void cancellationBeforeJournalSubmissionPreventsSubmission() {
        CheatTesterSession session = session();

        assertEquals(CheatTesterSession.FinishDisposition.NO_JOURNAL, session.beginFinishing());
        assertFalse(session.beginJournalSubmission());
    }

    @Test
    void cancellationDuringJournalSubmissionWaitsForCommittedRow() {
        CheatTesterSession session = session();

        assertTrue(session.beginJournalSubmission());
        assertEquals(CheatTesterSession.FinishDisposition.WAIT_FOR_JOURNAL, session.beginFinishing());
        assertFalse(session.markJournaledAndShouldBegin());
    }

    @Test
    void cancellationAfterJournalCommitUsesDurableFinishPath() {
        CheatTesterSession session = session();

        assertTrue(session.beginJournalSubmission());
        assertTrue(session.markJournaledAndShouldBegin());
        assertEquals(CheatTesterSession.FinishDisposition.JOURNALED, session.beginFinishing());
        assertEquals(CheatTesterSession.FinishDisposition.ALREADY_FINISHING, session.beginFinishing());
    }

    @Test
    void failedSubmissionCanReturnToNoJournalState() {
        CheatTesterSession session = session();

        assertTrue(session.beginJournalSubmission());
        session.cancelJournalSubmission();
        assertEquals(CheatTesterSession.FinishDisposition.NO_JOURNAL, session.beginFinishing());
    }

    private static CheatTesterSession session() {
        return new CheatTesterSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CheatTesterType.AUTO_ARMOR,
                true,
                Instant.parse("2026-08-07T20:00:00Z")
        );
    }
}
