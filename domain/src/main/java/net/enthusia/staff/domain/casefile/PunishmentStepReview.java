package net.enthusia.staff.domain.casefile;

public record PunishmentStepReview(
        int rawOrdinal,
        int effectiveOrdinal,
        int recencyBonus,
        String label,
        boolean escalationContributes
) {
    public PunishmentStepReview {
        if (rawOrdinal < 0 || effectiveOrdinal < 0 || recencyBonus < 0
                || label == null || label.isBlank()) {
            throw new IllegalArgumentException("punishment step review fields must be present");
        }
        label = label.trim();
    }
}
