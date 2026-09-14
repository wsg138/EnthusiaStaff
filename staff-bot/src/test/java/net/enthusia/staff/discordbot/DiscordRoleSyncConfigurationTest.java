package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscordRoleSyncConfigurationTest {
    @Test
    void parsesExplicitMappingsAndProjectsOnlyManagedRoles() {
        Map<String, String> values = new HashMap<>();
        values.put(DiscordRoleSyncConfiguration.MAPPINGS_ENV, "helper=1001;mod=1002;admin=1003");
        values.put(DiscordRoleSyncConfiguration.PROTECTED_ROLES_ENV, "9001,9002");
        values.put(DiscordRoleSyncConfiguration.MODE_ENV, "shadow");
        values.put(DiscordRoleSyncConfiguration.INTERVAL_SECONDS_ENV, "60");
        values.put(DiscordRoleSyncConfiguration.BATCH_SIZE_ENV, "12");

        DiscordRoleSyncConfiguration configuration =
                DiscordRoleSyncConfiguration.fromEnvironment(values).orElseThrow();

        assertEquals(DiscordRoleSyncConfiguration.Mode.SHADOW, configuration.mode());
        assertEquals(Set.of("1001", "1002", "1003"), configuration.managedRoleIds());
        assertEquals(Set.of("1001", "1003"), configuration.desiredRoles(Set.of("helper", "admin", "vip")));
        assertEquals(Set.of("9001", "9002"), configuration.protectedRoleIds());
        assertEquals(12, configuration.batchSize());
    }

    @Test
    void rejectsManagedProtectedOverlapAndMalformedMappings() {
        assertThrows(IllegalArgumentException.class, () -> DiscordRoleSyncConfiguration.fromEnvironment(Map.of(
                DiscordRoleSyncConfiguration.MAPPINGS_ENV, "mod=1002",
                DiscordRoleSyncConfiguration.PROTECTED_ROLES_ENV, "1002"
        )));
        assertThrows(IllegalArgumentException.class, () -> DiscordRoleSyncConfiguration.fromEnvironment(Map.of(
                DiscordRoleSyncConfiguration.MAPPINGS_ENV, "mod:1002"
        )));
    }

    @Test
    void partialRoleSyncConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> DiscordRoleSyncConfiguration.fromEnvironment(Map.of(
                DiscordRoleSyncConfiguration.MODE_ENV, "enforce"
        )));
        assertTrue(DiscordRoleSyncConfiguration.fromEnvironment(Map.of()).isEmpty());
    }
}
