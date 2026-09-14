package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdaNativeBanEnforcerTest {
    @Test
    void ownershipMarkerMatchesOnlyTheExactPunishment() {
        UUID punishmentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String reason = JdaNativeBanEnforcer.marker(punishmentId) + " repeated abuse";

        assertTrue(JdaNativeBanEnforcer.ownsReason(reason, punishmentId));
        assertFalse(JdaNativeBanEnforcer.ownsReason(
                reason,
                UUID.fromString("22222222-2222-2222-2222-222222222222")
        ));
        assertFalse(JdaNativeBanEnforcer.ownsReason("manual Discord ban", punishmentId));
        assertFalse(JdaNativeBanEnforcer.ownsReason(null, punishmentId));
    }
}
