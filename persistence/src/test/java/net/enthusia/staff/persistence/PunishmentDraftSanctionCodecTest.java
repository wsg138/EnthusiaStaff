package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentDraftSanctionCodecTest {
    private final PunishmentDraftSanctionCodec codec = new PunishmentDraftSanctionCodec(new ObjectMapper());

    @Test
    void roundTripsEveryLengthKindWithoutDurationLoss() throws JsonProcessingException {
        List<SanctionSpec> sanctions = List.of(
                new SanctionSpec(SanctionType.WARNING, SanctionLength.instant()),
                new SanctionSpec(
                        SanctionType.MUTE,
                        SanctionLength.temporary(Duration.ofDays(3).plusNanos(123_456_789))
                ),
                new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent())
        );

        assertEquals(sanctions, codec.decode(codec.encode(sanctions)));
    }

    @Test
    void rejectsMissingOrMalformedSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("[]"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("[{\"type\":\"MUTE\"}]"));
        assertThrows(JsonProcessingException.class, () -> codec.decode("not-json"));
    }
}
