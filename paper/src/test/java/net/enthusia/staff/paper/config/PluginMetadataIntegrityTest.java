package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class PluginMetadataIntegrityTest {
    @Test
    void corePaperMetadataTargetsTheExpectedRuntime() throws IOException {
        JsonNode metadata = pluginMetadata();

        assertEquals("EnthusiaStaff", metadata.path("name").asText());
        assertEquals("net.enthusia.staff.paper.EnthusiaStaffPaperPlugin", metadata.path("main").asText());
        assertEquals("1.21", metadata.path("api-version").asText());
        assertEquals("POSTWORLD", metadata.path("load").asText());
        assertFalse(metadata.path("description").asText().isBlank());
    }

    @Test
    void everyDeclaredCommandHasDescriptionAndUsage() throws IOException {
        JsonNode commands = pluginMetadata().path("commands");
        assertFalse(commands.isEmpty());

        commands.properties().forEach(entry -> {
            JsonNode command = entry.getValue();
            assertFalse(command.path("description").asText().isBlank(), entry.getKey() + " description");
            assertFalse(command.path("usage").asText().isBlank(), entry.getKey() + " usage");
            assertTrue(command.path("usage").asText().startsWith("/"), entry.getKey() + " usage prefix");
        });
    }

    @Test
    void onlyEstaffAndPlayerReportOmitAnOuterPermission() throws IOException {
        JsonNode commands = pluginMetadata().path("commands");
        Set<String> withoutOuterPermission = new HashSet<>();

        commands.properties().forEach(entry -> {
            if (entry.getValue().path("permission").isMissingNode()) {
                withoutOuterPermission.add(entry.getKey());
            }
        });

        assertEquals(Set.of("estaff", "report"), withoutOuterPermission);
    }

    @Test
    void everyCommandPermissionReferencesADeclaredPermission() throws IOException {
        JsonNode metadata = pluginMetadata();
        JsonNode permissions = metadata.path("permissions");
        Set<String> missingPermissions = new HashSet<>();

        metadata.path("commands").properties().forEach(entry -> {
            JsonNode permission = entry.getValue().path("permission");
            if (!permission.isMissingNode() && !permissions.has(permission.asText())) {
                missingPermissions.add(entry.getKey() + " -> " + permission.asText());
            }
        });

        assertTrue(missingPermissions.isEmpty(), "Undeclared command permissions: " + missingPermissions);
    }

    @Test
    void everyPermissionChildReferencesAnotherDeclaredPermission() throws IOException {
        JsonNode permissions = pluginMetadata().path("permissions");
        Set<String> missingChildren = new HashSet<>();

        permissions.properties().forEach(parent -> parent.getValue()
                .path("children")
                .properties()
                .forEach(child -> {
                    if (!permissions.has(child.getKey())) {
                        missingChildren.add(parent.getKey() + " -> " + child.getKey());
                    }
                }));

        assertTrue(missingChildren.isEmpty(), "Undeclared child permissions: " + missingChildren);
    }

    @Test
    void softDependenciesAreNonBlankAndUnique() throws IOException {
        JsonNode softDependencies = pluginMetadata().path("softdepend");
        assertTrue(softDependencies.isArray());

        Set<String> values = StreamSupport.stream(softDependencies.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());

        assertEquals(softDependencies.size(), values.size());
        values.forEach(value -> assertFalse(value.isBlank()));
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
