package net.enthusia.staff.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.migration.LegacySanction;
import net.enthusia.staff.domain.migration.LegacySanctionType;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import org.junit.jupiter.api.Test;

class LegacySanctionProjectionTest {
    private static final Instant ISSUED = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    @Test
    void keepsAnUnexpiredActiveSanctionActive() {
        LiteBansTargetImporter.LegacyProjection projection = LiteBansTargetImporter.project(
                sanction(true, Optional.of(NOW.plusSeconds(60)), Optional.empty()), NOW
        );

        assertEquals(SanctionStatus.ACTIVE, projection.status());
        assertTrue(projection.endedAt().isEmpty());
        assertTrue(projection.caseOpen());
    }

    @Test
    void treatsAStaleActiveFlagAsNaturallyExpired() {
        Instant expiration = NOW.minusSeconds(60);

        LiteBansTargetImporter.LegacyProjection projection = LiteBansTargetImporter.project(
                sanction(true, Optional.of(expiration), Optional.empty()), NOW
        );

        assertEquals(SanctionStatus.EXPIRED, projection.status());
        assertEquals(Optional.of(expiration), projection.endedAt());
        assertFalse(projection.caseOpen());
    }

    @Test
    void preservesAnEarlyRemovalInsteadOfCallingItExpired() {
        Instant expiration = NOW.minusSeconds(60);
        Instant removed = ISSUED.plusSeconds(60);

        LiteBansTargetImporter.LegacyProjection projection = LiteBansTargetImporter.project(
                sanction(false, Optional.of(expiration), Optional.of(removed)), NOW
        );

        assertEquals(SanctionStatus.ENDED_EARLY, projection.status());
        assertEquals(Optional.of(removed), projection.endedAt());
        assertFalse(projection.caseOpen());
    }

    @Test
    void preservesRemovalOfAPermanentSanction() {
        Instant removed = NOW.minusSeconds(60);

        LiteBansTargetImporter.LegacyProjection projection = LiteBansTargetImporter.project(
                sanction(false, Optional.empty(), Optional.of(removed)), NOW
        );

        assertEquals(SanctionStatus.ENDED_EARLY, projection.status());
        assertEquals(Optional.of(removed), projection.endedAt());
    }

    private static LegacySanction sanction(
            boolean active,
            Optional<Instant> expiration,
            Optional<Instant> endedAt
    ) {
        return new LegacySanction(
                "litebans_bans",
                "1",
                LegacySanctionType.BAN,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                Optional.of("Example"),
                "Cheating",
                "Staff",
                ISSUED,
                expiration,
                endedAt,
                Optional.empty(),
                active
        );
    }
}
