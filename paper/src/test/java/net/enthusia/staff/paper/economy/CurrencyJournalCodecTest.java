package net.enthusia.staff.paper.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CurrencyJournalCodecTest {
    @Test
    void snapshotRoundTripPreservesExactBinaryState() {
        CurrencyAccountState snapshot = new CurrencyAccountState(
                UUID.randomUUID(),
                20L,
                7L,
                new byte[]{0, 1, 2, -1},
                new byte[]{4, 5, 6},
                12L,
                8L,
                40L,
                "a".repeat(64)
        );
        CurrencyJournalCodec codec = new CurrencyJournalCodec(new ObjectMapper());

        assertEquals(snapshot, codec.snapshot(codec.snapshot(snapshot)));
    }
}
