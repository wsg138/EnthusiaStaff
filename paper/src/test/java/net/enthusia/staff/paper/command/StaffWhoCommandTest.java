package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class StaffWhoCommandTest {
    @Test
    void rendersOnlyAllowlistedOperationalFields() {
        List<String> lines = StaffWhoCommand.render(List.of(
                new StaffWhoCommand.Entry("Alpha", StaffRank.MOD, true, true),
                new StaffWhoCommand.Entry("Bravo", StaffRank.ADMIN, false, false)
        ), "3");

        assertEquals("Online staff: 2 | pending punishment requests: 3", lines.get(0));
        assertEquals("- Alpha [MOD] staff-mode=on vanished=yes", lines.get(1));
        assertEquals("- Bravo [ADMIN] staff-mode=off vanished=no", lines.get(2));
    }

    @Test
    void emptyRosterHasExplicitMessage() {
        assertEquals(List.of(
                "Online staff: 0 | pending punishment requests: unavailable",
                "- No staff are currently online."
        ), StaffWhoCommand.render(List.of(), "unavailable"));
    }

    @Test
    void pendingCountDoesNotPretendFiveHundredIsExhaustive() {
        assertEquals("499", StaffWhoCommand.pendingLabel(499));
        assertEquals("500+", StaffWhoCommand.pendingLabel(500));
        assertEquals("500+", StaffWhoCommand.pendingLabel(501));
    }
}
