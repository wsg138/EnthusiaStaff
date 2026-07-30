package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandPermissionGateTest {
    private static final String STAFF_MODE = "enthusiastaff.staffmode";

    @Test
    void exactPermissionGrantIsRequired() {
        Set<String> permissions = Set.of(STAFF_MODE);

        assertTrue(CommandPermissionGate.allows(permissions::contains, STAFF_MODE));
        assertFalse(CommandPermissionGate.allows(permissions::contains, "enthusiastaff.vanish"));
    }

    @Test
    void missingOrBlankPermissionFailsClosed() {
        assertFalse(CommandPermissionGate.allows(ignored -> true, null));
        assertFalse(CommandPermissionGate.allows(ignored -> true, ""));
        assertFalse(CommandPermissionGate.allows(ignored -> true, "   "));
    }
}
