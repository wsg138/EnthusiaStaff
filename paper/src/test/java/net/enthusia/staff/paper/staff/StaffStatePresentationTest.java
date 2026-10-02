package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class StaffStatePresentationTest {
    @Test
    void staffModeAlwaysShowsExplicitVanishOnState() {
        assertEquals(
                Component.text("STAFF MODE", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("VANISH ", NamedTextColor.GRAY))
                        .append(Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)),
                StaffStatePresentation.indicator(true, true)
        );
    }

    @Test
    void staffModeAlwaysShowsExplicitVanishOffState() {
        assertEquals(
                Component.text("STAFF MODE", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("VANISH ", NamedTextColor.GRAY))
                        .append(Component.text("OFF", NamedTextColor.GOLD, TextDecoration.BOLD)),
                StaffStatePresentation.indicator(true, false)
        );
    }

    @Test
    void vanishOutsideStaffModeStillHasPersistentIndicator() {
        assertEquals(
                Component.text("VANISH", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)),
                StaffStatePresentation.indicator(false, true)
        );
    }
}
