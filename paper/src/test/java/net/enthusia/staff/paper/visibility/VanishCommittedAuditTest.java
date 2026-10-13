package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class VanishCommittedAuditTest {
    @Test
    void successfulVisibilityTransitionsHaveStableAuditEventNames() {
        assertEquals(Optional.of("vanish-enabled"),
                VanishManager.committedVanishAuditAction(false, true));
        assertEquals(Optional.of("vanish-disabled"),
                VanishManager.committedVanishAuditAction(true, false));
    }

    @Test
    void IdempotentPersistenceDoesNotCreateAnotherVanishToggleAudit() {
        assertTrue(VanishManager.committedVanishAuditAction(false, false).isEmpty());
        assertTrue(VanishManager.committedVanishAuditAction(true, true).isEmpty());
    }
}
