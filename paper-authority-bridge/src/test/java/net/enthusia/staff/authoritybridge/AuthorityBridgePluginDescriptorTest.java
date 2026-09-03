package net.enthusia.staff.authoritybridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuthorityBridgePluginDescriptorTest {
    @Test
    void descriptorHasNoPlayerFacingCommandsOrPermissions() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(input);
            String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(descriptor.contains("commands:"));
            assertFalse(descriptor.contains("permissions:"));
            assertTrue(descriptor.contains("depend:\n  - LuckPerms"));
            assertTrue(descriptor.contains("EnthusiaStaffAuthorityBridge"));
        }
    }
}
