package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

final class PolicyV2JsonCodecTest {
    private final PolicyV2JsonCodec codec = new PolicyV2JsonCodec();

    @Test
    void sanctionLengthsRoundTripWithoutDerivedBooleanProperties() {
        assertRoundTrip(SanctionLength.instant());
        assertRoundTrip(SanctionLength.permanent());
        assertRoundTrip(SanctionLength.temporary(Duration.ofMinutes(15)));
    }

    private void assertRoundTrip(SanctionLength length) {
        String json = codec.write(length);

        assertFalse(json.contains("\"instant\""));
        assertFalse(json.contains("\"permanent\""));
        assertEquals(length, codec.read(json, SanctionLength.class));
    }
}
