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
    void committedAuditSupportsDisconnectedPlayersWithoutBukkitPlayerArgument() throws Exception {
        // The committed state is audited by UUID/name/rank after persistence.
        // It must not depend on the availability of an online Bukkit Player.
        var method = net.enthusia.staff.paper.staff.StaffModeManager.class.getMethod(
                "logCommittedVisibilityTransition",
                java.util.UUID.class, String.class,
                net.enthusia.staff.domain.auth.StaffRank.class, boolean.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void idempotentPersistenceDoesNotCreateAnotherVanishToggleAudit() {
        assertTrue(VanishManager.committedVanishAuditAction(false, false).isEmpty());
        assertTrue(VanishManager.committedVanishAuditAction(true, true).isEmpty());
    }
}
