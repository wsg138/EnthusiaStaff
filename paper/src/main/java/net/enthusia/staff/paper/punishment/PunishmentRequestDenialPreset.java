package net.enthusia.staff.paper.punishment;

import java.util.Arrays;
import org.bukkit.Material;

enum PunishmentRequestDenialPreset {
    INSUFFICIENT_EVIDENCE(
            10,
            Material.PAPER,
            "Insufficient evidence",
            "Denied: insufficient evidence supports the requested punishment"
    ),
    INCORRECT_CLASSIFICATION(
            12,
            Material.WRITABLE_BOOK,
            "Incorrect reason or classification",
            "Denied: the selected reason or classification is incorrect"
    ),
    INAPPROPRIATE_SANCTION(
            14,
            Material.ANVIL,
            "Requested sanction is not appropriate",
            "Denied: the requested sanction is not appropriate for the evidence"
    ),
    DUPLICATE_OR_HANDLED(
            16,
            Material.BARRIER,
            "Duplicate or already handled",
            "Denied: duplicate request or the incident was already handled"
    );

    private final int slot;
    private final Material material;
    private final String label;
    private final String auditNote;

    PunishmentRequestDenialPreset(int slot, Material material, String label, String auditNote) {
        this.slot = slot;
        this.material = material;
        this.label = label;
        this.auditNote = auditNote;
    }

    int slot() {
        return slot;
    }

    Material material() {
        return material;
    }

    String label() {
        return label;
    }

    String auditNote() {
        return auditNote;
    }

    static PunishmentRequestDenialPreset fromSlot(int slot) {
        return Arrays.stream(values())
                .filter(preset -> preset.slot == slot)
                .findFirst()
                .orElse(null);
    }
}
