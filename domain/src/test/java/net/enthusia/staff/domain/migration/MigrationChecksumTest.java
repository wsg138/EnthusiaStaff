package net.enthusia.staff.domain.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MigrationChecksumTest {
    private final MigrationChecksum checksum = new MigrationChecksum();

    @Test
    void checksumIsOrderIndependentAndSensitiveToExpiration() {
        LegacySanction first = sanction("1", Optional.of(Instant.parse("2026-08-01T00:00:00Z")));
        LegacySanction second = sanction("2", Optional.empty());

        assertEquals(checksum.calculate(List.of(first, second)), checksum.calculate(List.of(second, first)));
        LegacySanction changed = sanction("1", Optional.of(Instant.parse("2026-08-02T00:00:00Z")));
        assertNotEquals(checksum.calculate(List.of(first)), checksum.calculate(List.of(changed)));
    }

    private static LegacySanction sanction(String externalId, Optional<Instant> expiration) {
        return new LegacySanction(
                "litebans_bans",
                externalId,
                LegacySanctionType.BAN,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                Optional.of("Example"),
                "Cheating",
                "Staff",
                Instant.parse("2026-07-01T00:00:00Z"),
                expiration,
                true
        );
    }
}
