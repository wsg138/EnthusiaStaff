package net.enthusia.staff.domain.application;

/** Public-safe configured reason metadata for moderation selection surfaces. */
public record PunishmentReasonOption(String id, String family, String label) {
    public PunishmentReasonOption {
        if (id == null || id.isBlank() || family == null || family.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("punishment reason option fields must be present");
        }
        id = id.trim();
        family = family.trim();
        label = label.trim();
    }
}
