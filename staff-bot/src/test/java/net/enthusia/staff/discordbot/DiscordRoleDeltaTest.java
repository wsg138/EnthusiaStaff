package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscordRoleDeltaTest {
    private static final String HELPER_ROLE = "1001";
    private static final String MOD_ROLE = "1002";

    @Test
    void unmanagedRolesAreNeverRemovalCandidates() {
        DiscordRoleDelta delta = DiscordRoleDelta.calculate(
                Set.of(MOD_ROLE),
                Set.of(HELPER_ROLE, "7000", "9000"),
                Set.of(HELPER_ROLE, MOD_ROLE)
        );

        assertEquals(Set.of(MOD_ROLE), delta.add());
        assertEquals(Set.of(HELPER_ROLE), delta.remove());
    }

    @Test
    void rejectsDesiredRolesOutsideManagedAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> DiscordRoleDelta.calculate(
                Set.of("7777"), Set.of(), Set.of(HELPER_ROLE)
        ));
    }
}
