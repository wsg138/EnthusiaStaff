package net.enthusia.staff.domain.casefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.SanctionChangeExpectation;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class CaseReviewTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void activeSanctionsAndChangeExpectationReflectCurrentReview() {
        UUID activeId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();
        CaseReview review = review(List.of(
                sanction(activeId, SanctionStatus.ACTIVE, 4L),
                sanction(expiredId, SanctionStatus.EXPIRED, 7L)
        ));

        assertTrue(review.hasActiveSanctions());

        SanctionChangeExpectation expectation = review.changeExpectation();
        assertEquals(9L, expectation.caseRevision());
        assertEquals(4L, expectation.sanctionRevisions().get(activeId));
        assertEquals(7L, expectation.sanctionRevisions().get(expiredId));
        assertEquals(Optional.empty(), expectation.escalationContributes());
        assertEquals(Optional.empty(), expectation.openOverturnRequestId());
        assertThrows(
                UnsupportedOperationException.class,
                () -> expectation.sanctionRevisions().put(UUID.randomUUID(), 1L)
        );
    }

    @Test
    void terminalSanctionsAreNotActive() {
        for (SanctionStatus status : List.of(
                SanctionStatus.EXPIRED,
                SanctionStatus.ENDED_EARLY,
                SanctionStatus.REVOKED,
                SanctionStatus.OVERTURNED
        )) {
            assertFalse(sanction(UUID.randomUUID(), status, 0L).active());
        }
        assertTrue(sanction(UUID.randomUUID(), SanctionStatus.PENDING, 0L).active());
        assertTrue(sanction(UUID.randomUUID(), SanctionStatus.ACTIVE, 0L).active());
        assertTrue(sanction(UUID.randomUUID(), SanctionStatus.APPLIED, 0L).active());
        assertFalse(review(List.of()).hasActiveSanctions());
    }

    @Test
    void invalidReviewAndSanctionFieldsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> sanction(UUID.randomUUID(), SanctionStatus.ACTIVE, -1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SanctionReview(
                        null,
                        SanctionType.BAN,
                        SanctionStatus.ACTIVE,
                        ISSUED_AT,
                        Optional.empty(),
                        Optional.empty(),
                        0L
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CaseReview(
                        new CaseId("0123456789ABCDEF"),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        " ",
                        "ADMIN",
                        "reason",
                        "reason.id",
                        "BAN",
                        "internal",
                        "v1",
                        CaseVisibility.PRIVATE,
                        CaseState.OPEN,
                        ISSUED_AT,
                        0L,
                        Optional.empty(),
                        List.of(),
                        Optional.empty()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SanctionChangeExpectation(-1L, java.util.Map.of(), Optional.empty(), Optional.empty())
        );
    }

    private static CaseReview review(List<SanctionReview> sanctions) {
        return new CaseReview(
                new CaseId("0123456789ABCDEF"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Moderator",
                "ADMIN",
                "Public reason",
                "chat.abuse",
                "MUTE",
                "Internal explanation",
                "rules-v1",
                CaseVisibility.PUBLIC,
                CaseState.OPEN,
                ISSUED_AT,
                9L,
                Optional.empty(),
                sanctions,
                Optional.empty()
        );
    }

    private static SanctionReview sanction(UUID id, SanctionStatus status, long revision) {
        return new SanctionReview(
                id,
                SanctionType.MUTE,
                status,
                ISSUED_AT,
                Optional.empty(),
                Optional.empty(),
                revision
        );
    }
}
