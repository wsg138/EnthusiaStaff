package net.enthusia.staff.domain.casefile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentStepReviewTest {
    private static final List<SanctionSpec> RECOMMENDATION = List.of(
            new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())
    );

    @Test
    void acceptsCompleteRecommendationSnapshot() {
        assertDoesNotThrow(() -> new PunishmentStepReview(
                1,
                1,
                Optional.of(1),
                0,
                "Warning",
                Optional.of(RECOMMENDATION),
                true
        ));
    }

    @Test
    void rejectsSelectedOrdinalWithoutRecommendation() {
        assertThrows(IllegalArgumentException.class, () -> new PunishmentStepReview(
                1,
                1,
                Optional.of(1),
                0,
                "Warning",
                Optional.empty(),
                true
        ));
    }

    @Test
    void rejectsRecommendationWithoutSelectedOrdinal() {
        assertThrows(IllegalArgumentException.class, () -> new PunishmentStepReview(
                1,
                1,
                Optional.empty(),
                0,
                "Warning",
                Optional.of(RECOMMENDATION),
                true
        ));
    }

    @Test
    void rejectsEmptyRecommendation() {
        assertThrows(IllegalArgumentException.class, () -> new PunishmentStepReview(
                1,
                1,
                Optional.of(1),
                0,
                "Warning",
                Optional.of(List.of()),
                true
        ));
    }
}
