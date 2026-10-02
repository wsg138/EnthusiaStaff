package net.enthusia.staff.paper.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class StaffMessageStyleTest {
    @Test
    void shadowMigrationUsesWarningPaletteInsteadOfErrorPalette() {
        Component header = StaffMessageStyle.modeHeader("EnthusiaStaff", OperationalMode.SHADOW_MIGRATION);

        assertEquals(NamedTextColor.AQUA, header.color());
        assertEquals(NamedTextColor.GOLD, header.children().get(1).color());
        assertEquals(
                StaffMessageStyle.Tone.WARNING,
                StaffMessageStyle.issueTone("cutover", "authority remains gated", OperationalMode.SHADOW_MIGRATION)
        );
    }

    @Test
    void actualInfrastructureFailuresRemainErrors() {
        assertEquals(
                StaffMessageStyle.Tone.ERROR,
                StaffMessageStyle.issueTone("mariadb", "connection failed", OperationalMode.DEGRADED)
        );
        assertEquals(NamedTextColor.RED, StaffMessageStyle.style("Operation failed; nothing changed.").color());
    }

    @Test
    void blockingConditionsAndConfirmationsUseDistinctTones() {
        assertEquals(
                NamedTextColor.RED,
                StaffMessageStyle.style("You cannot enter staff mode while combat tagged.").color()
        );
        assertEquals(
                NamedTextColor.RED,
                StaffMessageStyle.style("The bounded work queue is full; nothing changed.").color()
        );
        assertEquals(
                NamedTextColor.GOLD,
                StaffMessageStyle.style("Use /unlink CONFIRM to continue.").color()
        );
        assertEquals(
                NamedTextColor.GOLD,
                StaffMessageStyle.style("This option is only available while spectating.").color()
        );
    }

    @Test
    void usageHighlightsCommandWithoutWhiteningTheWholeLine() {
        Component usage = StaffMessageStyle.usage("Usage: /estaff verify full");

        assertEquals(NamedTextColor.GRAY, usage.color());
        assertEquals(NamedTextColor.AQUA, usage.children().getFirst().color());
    }

    @Test
    void interactiveComponentsAreNeverRewritten() {
        Component interactive = Component.text("[Inspect]")
                .clickEvent(ClickEvent.runCommand("/inspect Example"));

        assertSame(interactive, StaffMessageStyle.style(interactive));
    }
}
