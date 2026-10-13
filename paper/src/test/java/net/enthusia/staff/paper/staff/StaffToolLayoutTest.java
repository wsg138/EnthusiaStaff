package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StaffToolLayoutTest {
    private static final String SESSION = "session";
    @Test void layoutDeterminesStartingPositionsButOwnersMayRearrange() {
        var config = new YamlConfiguration();
        config.set("staff-tools.slots.random-teleport", 1);
        config.set("staff-tools.slots.player-inspector", 0);
        var layout = StaffToolLayout.load(config);
        UUID owner = UUID.randomUUID();
        var tool = StaffToolDefinition.RANDOM_TELEPORT;
        assertEquals(StaffToolSessionPolicy.Status.VALID, StaffToolSessionPolicy.validate(owner, SESSION, 1,
                tool, new StaffToolSessionPolicy.ItemContext(tool.material(), layout.slot(tool)), owner.toString(), SESSION, StaffRank.ADMIN));
        assertEquals(StaffToolSessionPolicy.Status.VALID, StaffToolSessionPolicy.validate(owner, SESSION, 0,
                tool, new StaffToolSessionPolicy.ItemContext(tool.material(), layout.slot(tool)), owner.toString(), SESSION, StaffRank.ADMIN));
        assertEquals(StaffToolSessionPolicy.Status.OWNER_MISMATCH, StaffToolSessionPolicy.validate(owner, SESSION, 1,
                tool, new StaffToolSessionPolicy.ItemContext(tool.material(), layout.slot(tool)), UUID.randomUUID().toString(), SESSION, StaffRank.ADMIN));
    }
    @Test void refusesDuplicateOutOfRangeUnknownAndNonIntegerSlots() {
        for (Object value : new Object[]{1, 9, -1, "0"}) {
            assertInvalidSlot(value);
        }
        var unknown = new YamlConfiguration();
        unknown.set("staff-tools.slots.console-command", 0);
        assertThrows(IllegalArgumentException.class, () -> StaffToolLayout.load(unknown));
    }
    private static void assertInvalidSlot(Object value) {
        var config = new YamlConfiguration();
        config.set("staff-tools.slots.random-teleport", value);
        assertThrows(IllegalArgumentException.class, () -> StaffToolLayout.load(config));
    }
}
