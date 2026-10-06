package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class UnrestrictedStaffProfileTest {
    @Test
    void creativeAndSpectatorArePreservedOnStaffEntry() {
        assertEquals(GameMode.CREATIVE, StaffModeManager.unrestrictedEntryGameMode(GameMode.CREATIVE));
        assertEquals(GameMode.SPECTATOR, StaffModeManager.unrestrictedEntryGameMode(GameMode.SPECTATOR));
    }

    @Test
    void gameplayModesEnterCreativeForUnrestrictedStaff() {
        assertEquals(GameMode.CREATIVE, StaffModeManager.unrestrictedEntryGameMode(GameMode.SURVIVAL));
        assertEquals(GameMode.CREATIVE, StaffModeManager.unrestrictedEntryGameMode(GameMode.ADVENTURE));
    }
}
