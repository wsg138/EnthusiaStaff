package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaffModeVanishEntryOptionTest {
    @Test
    void parsesRememberedAndExplicitEntryForms() {
        assertEquals(StaffModeVanishEntryOption.REMEMBERED,
                StaffModeVanishEntryOption.parse(new String[0]).orElseThrow());
        assertEquals(StaffModeVanishEntryOption.VANISHED,
                StaffModeVanishEntryOption.parse(new String[]{"-v"}).orElseThrow());
        assertEquals(StaffModeVanishEntryOption.VANISHED,
                StaffModeVanishEntryOption.parse(new String[]{"VANISH"}).orElseThrow());
        assertEquals(StaffModeVanishEntryOption.VISIBLE,
                StaffModeVanishEntryOption.parse(new String[]{"-nv"}).orElseThrow());
        assertEquals(StaffModeVanishEntryOption.VISIBLE,
                StaffModeVanishEntryOption.parse(new String[]{"visible"}).orElseThrow());
        assertTrue(StaffModeVanishEntryOption.parse(new String[]{"unexpected"}).isEmpty());
        assertTrue(StaffModeVanishEntryOption.parse(new String[]{"-v", "extra"}).isEmpty());
    }

    @Test
    void explicitOverrideWinsAndIsRemembered() {
        var vanished = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.VANISHED, Optional.of(false));
        var visible = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.VISIBLE, Optional.of(true));

        assertTrue(vanished.desired());
        assertTrue(vanished.rememberChoice());
        assertFalse(visible.desired());
        assertTrue(visible.rememberChoice());
    }

    @Test
    void rememberedPreferenceIsUsedWithoutRewritingIt() {
        var vanished = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.REMEMBERED, Optional.of(true));
        var visible = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.REMEMBERED, Optional.of(false));

        assertTrue(vanished.desired());
        assertFalse(vanished.rememberChoice());
        assertFalse(visible.desired());
        assertFalse(visible.rememberChoice());
    }

    @Test
    void firstEntryDefaultsToVanishOnAndPersistsThatChoice() {
        var first = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.REMEMBERED, Optional.empty());

        assertTrue(first.desired());
        assertTrue(first.rememberChoice());
    }
}
