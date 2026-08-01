package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VerificationResultTest {
    @Test
    void acceptedIsTrueOnlyForAcceptedStatus() {
        assertTrue(new VerificationResult(VerificationStatus.ACCEPTED).accepted());

        Arrays.stream(VerificationStatus.values())
                .filter(status -> status != VerificationStatus.ACCEPTED)
                .forEach(status -> assertFalse(new VerificationResult(status).accepted(), status.name()));
    }

    @Test
    void nullStatusIsNotAccepted() {
        assertFalse(new VerificationResult(null).accepted());
    }
}
