package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaffModeVanishEntryOptionTest {
    @Test
    void parsesRememberedAndExplicitEntryForms() {
        assertEquals(
                StaffModeVanishEntryOption.REMEMBERED,
                StaffModeVanishEntryOption.parse(new String[0]).orElseThrow()
        );
        assertEquals(
                StaffModeVanishEntryOption.VANISHED,
                StaffModeVanishEntryOption.parse(new String[]{"-v"}).orElseThrow()
        );
        assertEquals(
                StaffModeVanishEntryOption.VANISHED,
                StaffModeVanishEntryOption.parse(new String[]{"VANISH"}).orElseThrow()
        );
        assertEquals(
                StaffModeVanishEntryOption.VISIBLE,
                StaffModeVanishEntryOption.parse(new String[]{"-nv"}).orElseThrow()
        );
        assertEquals(
                StaffModeVanishEntryOption.VISIBLE,
                StaffModeVanishEntryOption.parse(new String[]{"visible"}).orElseThrow()
        );
        assertTrue(StaffModeVanishEntryOption.parse(new String[]{"unexpected"}).isEmpty());
        assertTrue(StaffModeVanishEntryOption.parse(new String[]{"-v", "extra"}).isEmpty());
    }

    @Test
    void explicitOverrideWinsOverRememberedPreference() {
        StaffModeVanishEntryCoordinator.EntryChoice vanished = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.VANISHED,
                Optional.of(false)
        );
        StaffModeVanishEntryCoordinator.EntryChoice visible = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.VISIBLE,
                Optional.of(true)
        );

        assertTrue(vanished.desired());
        assertTrue(vanished.persistIfUnchanged());
        assertFalse(visible.desired());
        assertTrue(visible.persistIfUnchanged());
    }

    @Test
    void rememberedPreferenceIsUsedWithoutRewritingIt() {
        StaffModeVanishEntryCoordinator.EntryChoice vanished = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.REMEMBERED,
                Optional.of(true)
        );
        StaffModeVanishEntryCoordinator.EntryChoice visible = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.REMEMBERED,
                Optional.of(false)
        );

        assertTrue(vanished.desired());
        assertFalse(vanished.persistIfUnchanged());
        assertFalse(visible.desired());
        assertFalse(visible.persistIfUnchanged());
    }

    @Test
    void firstEntryDefaultsToVanishOnAndPersistsThatChoice() {
        StaffModeVanishEntryCoordinator.EntryChoice first = StaffModeVanishEntryCoordinator.resolveChoice(
                StaffModeVanishEntryOption.REMEMBERED,
                Optional.empty()
        );

        assertTrue(first.desired());
        assertTrue(first.persistIfUnchanged());
    }
}
