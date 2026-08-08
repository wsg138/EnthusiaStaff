package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class FakeBasePluginMetadataTest {
    @Test
    void fakeBasePermissionsRemainExplicitlyLeastPrivilege() throws IOException {
        try (InputStream stream = FakeBasePluginMetadataTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            JsonNode permissions = new ObjectMapper(new YAMLFactory()).readTree(stream).path("permissions");
            JsonNode base = permissions.path(FakeBaseManager.PERMISSION);
            JsonNode manageAny = permissions.path(FakeBaseManager.MANAGE_ANY_PERMISSION);

            assertEquals("false", base.path("default").asText());
            assertEquals("false", manageAny.path("default").asText());
            assertEquals("true", manageAny.path("children").path(FakeBaseManager.PERMISSION).asText());
        }
    }
}
