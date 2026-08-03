package net.enthusia.staff.domain.casefile;

import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentStepReview(
        int rawOrdinal,
        int effectiveOrdinal,
        Optional<Integer> selectedOrdinal,
        int recencyBonus,
        String label,
        Optional<List<SanctionSpec>> recommendedSanctions,
        boolean escalationContributes
) {
    public PunishmentStepReview {
        if (rawOrdinal < 0 || effectiveOrdinal < 0 || selectedOrdinal == null
                || recencyBonus < 0 || label == null || label.isBlank()
                || recommendedSanctions == null) {
            throw new IllegalArgumentException("punishment step review fields must be present");
        }
        selectedOrdinal = selectedOrdinal.map(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("selected punishment ordinal cannot be negative");
            }
            return value;
        });
        label = label.trim();
        recommendedSanctions = recommendedSanctions.map(List::copyOf);
        if (recommendedSanctions.filter(List::isEmpty).isPresent()) {
            throw new IllegalArgumentException("stored punishment recommendation cannot be empty");
        }
    }
}
