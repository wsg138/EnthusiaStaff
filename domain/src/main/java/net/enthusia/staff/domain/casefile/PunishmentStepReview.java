package net.enthusia.staff.domain.casefile;

import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentStepReview(
        int rawOrdinal,
        int effectiveOrdinal,
        int recencyBonus,
        String label,
        Optional<List<SanctionSpec>> recommendedSanctions,
        boolean escalationContributes
) {
    public PunishmentStepReview {
        if (rawOrdinal < 0 || effectiveOrdinal < 0 || recencyBonus < 0
                || label == null || label.isBlank() || recommendedSanctions == null) {
            throw new IllegalArgumentException("punishment step review fields must be present");
        }
        label = label.trim();
        recommendedSanctions = recommendedSanctions.map(List::copyOf);
        if (recommendedSanctions.filter(List::isEmpty).isPresent()) {
            throw new IllegalArgumentException("stored punishment recommendation cannot be empty");
        }
    }
}
