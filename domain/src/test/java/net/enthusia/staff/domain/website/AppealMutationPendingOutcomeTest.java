package net.enthusia.staff.domain.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AppealMutationPendingOutcomeTest {
    @Test
    void roundTripsPendingRevision() {
        String encoded = AppealMutationPendingOutcome.encode(42L);

        assertEquals("MUTATION_PENDING_R42", encoded);
        assertEquals(42L, AppealMutationPendingOutcome.parse(encoded).orElseThrow());
    }

    @Test
    void ignoresNonPendingOutcomes() {
        assertTrue(AppealMutationPendingOutcome.parse(null).isEmpty());
        assertTrue(AppealMutationPendingOutcome.parse("APPLIED").isEmpty());
    }

    @Test
    void rejectsMalformedOrNegativePendingRevisions() {
        assertThrows(
                NumberFormatException.class,
                () -> AppealMutationPendingOutcome.parse("MUTATION_PENDING_Rinvalid")
        );
        assertThrows(
                NumberFormatException.class,
                () -> AppealMutationPendingOutcome.parse("MUTATION_PENDING_R-1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AppealMutationPendingOutcome.encode(-1L)
        );
    }
}
