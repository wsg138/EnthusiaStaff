package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandPermissionConfigurationTest {
    private static final Map<String, String> EXPECTED_PERMISSIONS = Map.of(
            "staff", "enthusiastaff.staffmode",
            "vanish", "enthusiastaff.vanish",
            "freeze", "enthusiastaff.freeze",
            "unfreeze", "enthusiastaff.freeze",
            "invsee", "enthusiastaff.inventory.view",
            "endersee", "enthusiastaff.inventory.view",
            "inspect", "enthusiastaff.inspect"
    );

    @Test
    void staffOnlyCommandsRetainTheirOuterPermissionBoundary() throws IOException {
        JsonNode commands = pluginMetadata().path("commands");

        EXPECTED_PERMISSIONS.forEach((command, permission) -> assertEquals(
                permission,
                commands.path(command).path("permission").asText(),
                command
        ));
    }

    private static JsonNode pluginMetadata() throws IOException {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("plugin.yml")) {
            if (input == null) {
                throw new IOException("plugin.yml is absent from the test classpath");
            }
            return new ObjectMapper(new YAMLFactory()).readTree(input);
        }
    }
}
