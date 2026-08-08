package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FakeBasePluginMetadataTest {
    @Test
    void pluginMetadataDeclaresCommandAndPermissionFallbacks() throws IOException {
        try (InputStream stream = FakeBasePluginMetadataTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            String pluginYaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(pluginYaml.contains("/cheattester <select|run|cancel|status|config|base>"));
            assertTrue(pluginYaml.contains("enthusiastaff.cheattester.fake-base:"));
            assertTrue(pluginYaml.contains("enthusiastaff.cheattester.fake-base.manage-any:"));
        }
    }
}
