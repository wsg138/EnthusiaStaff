package net.enthusia.staff.domain.application;

import net.enthusia.staff.domain.casefile.CaseVisibility;

/** Public-safe configured reason metadata for moderation selection surfaces. */
public record PunishmentReasonOption(
        String id,
        String family,
        String label,
        CaseVisibility defaultVisibility
) {
    public PunishmentReasonOption(String id, String family, String label) {
        this(id, family, label, CaseVisibility.PUBLIC);
    }

    public PunishmentReasonOption {
        if (id == null || id.isBlank() || family == null || family.isBlank() || label == null || label.isBlank()
                || defaultVisibility == null) {
            throw new IllegalArgumentException("punishment reason option fields must be present");
        }
        id = id.trim();
        family = family.trim();
        label = label.trim();
    }
}
