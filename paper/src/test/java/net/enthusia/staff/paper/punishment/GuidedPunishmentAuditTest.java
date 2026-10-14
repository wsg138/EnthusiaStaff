package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuidedPunishmentAuditTest {
    private static final String BASE = "Guided incident v1 | category=chat | "
            + "medium=public-chat | conduct=spam | extent=sustained | "
            + "continued=not-applicable | incidentImpact=25 | staffPreference=STRONGER";

    @Test
    void aNonDefaultStaffSelectionRequiresAnExplicitRationale() {
        assertTrue(PunishmentGuiController.guidedNeedsStaffRationale(BASE));
        String written = PunishmentGuiController.guidedAuditExplanation(
                BASE, "Long-running flooding across several chat channels");
        assertFalse(PunishmentGuiController.guidedNeedsStaffRationale(written));
        assertTrue(written.startsWith(BASE));
        assertTrue(written.contains(" | Staff note: Long-running flooding"));
    }

    @Test
    void subsequentEditsPreserveFactsWithoutAppendingDuplicateNotes() {
        String first = PunishmentGuiController.guidedAuditExplanation(BASE, "Initial rationale");
        String revised = PunishmentGuiController.guidedAuditExplanation(first, "Revised rationale");
        assertEquals(BASE + " | Staff note: Revised rationale", revised);
        assertEquals(revised, PunishmentGuiController.guidedAuditExplanation(revised, revised));
    }

    @Test
    void defaultGuidedSelectionAndLegacyNotesRemainCompatible() {
        assertFalse(PunishmentGuiController.guidedNeedsStaffRationale(
                BASE.replace("STRONGER", "BALANCED")));
        assertFalse(PunishmentGuiController.guidedNeedsStaffRationale("manual note"));
        assertEquals("replacement", PunishmentGuiController.guidedAuditExplanation(
                "original legacy note", "replacement"));
    }
}
