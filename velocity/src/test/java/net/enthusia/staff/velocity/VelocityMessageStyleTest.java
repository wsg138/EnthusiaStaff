package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class VelocityMessageStyleTest {
    @Test
    void shadowMigrationHeaderIsGoldAndNotRed() {
        Component header = VelocityMessageStyle.modeHeader("EnthusiaStaff", OperationalMode.SHADOW_MIGRATION);

        assertEquals(NamedTextColor.AQUA, header.color());
        assertEquals(NamedTextColor.GOLD, header.children().get(1).color());
    }

    @Test
    void permissionDenialsAreErrorsAndDisabledStatesAreWarnings() {
        assertEquals(
                NamedTextColor.RED,
                VelocityMessageStyle.style("You do not have permission to manage cutover.").color()
        );
        assertEquals(
                NamedTextColor.GOLD,
                VelocityMessageStyle.style("Website API: DISABLED or unavailable").color()
        );
    }

    @Test
    void blockingConditionsAndConfirmationsUseDistinctTones() {
        assertEquals(
                NamedTextColor.RED,
                VelocityMessageStyle.style("The bounded work queue is full; nothing changed.").color()
        );
        assertEquals(
                NamedTextColor.GOLD,
                VelocityMessageStyle.style("Use CONFIRM before continuing.").color()
        );
        assertEquals(
                NamedTextColor.GOLD,
                VelocityMessageStyle.style("This option is only available in migration mode.").color()
        );
    }

    @Test
    void usageHighlightsTheCommand() {
        Component usage = VelocityMessageStyle.usage("Usage: /estaff verify full");

        assertEquals(NamedTextColor.GRAY, usage.color());
        assertEquals(NamedTextColor.AQUA, usage.children().getFirst().color());
    }

    @Test
    void interactiveComponentsArePreserved() {
        Component interactive = Component.text("[Open]")
                .clickEvent(ClickEvent.runCommand("/estaff status"));

        assertSame(interactive, VelocityMessageStyle.style(interactive));
    }
}
