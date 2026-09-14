package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscordRoleDeltaTest {
    @Test
    void unmanagedRolesAreNeverRemovalCandidates() {
        DiscordRoleDelta delta = DiscordRoleDelta.calculate(
                Set.of("1002"),
                Set.of("1001", "7000", "9000"),
                Set.of("1001", "1002")
        );

        assertEquals(Set.of("1002"), delta.add());
        assertEquals(Set.of("1001"), delta.remove());
    }

    @Test
    void rejectsDesiredRolesOutsideManagedAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> DiscordRoleDelta.calculate(
                Set.of("7777"), Set.of(), Set.of("1001")
        ));
    }
}
