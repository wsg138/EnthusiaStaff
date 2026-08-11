package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class InventoryWorkflowWiringTest {
    private static final String VIEW_PERMISSION = "enthusiastaff.inventory.view";
    private static final String EDIT_PERMISSION = "enthusiastaff.inventory.edit";
    private static final String DEFAULT_FIELD = "default";
    private static final Path REGISTRAR_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
    );
    private static final Path COMMAND_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/InventoryCommand.java"
    );
    private static final Path COORDINATOR_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/inventory/InventoryCoordinator.java"
    );

    @Test
    void registrarBindsInvseeAndEnderseeToTheSameInventoryWorkflow() throws IOException {
        String source = normalizedSource(REGISTRAR_SOURCE);

        assertTrue(source.contains("List.of(\"invsee\", \"endersee\")"));
        assertTrue(source.contains("InventoryCommand command = new InventoryCommand("));
        assertTrue(source.contains("dependencies.players().inventory(), workers()"));
        assertTrue(source.contains("INVENTORY_COMMANDS.forEach(name -> bindCompleting(name, command, command));"));
    }

    @Test
    void commandUsesAuthoritativeDirectoryAndEntitySchedulerBeforeOpening() throws IOException {
        String source = normalizedSource(COMMAND_SOURCE);

        assertTrue(source.contains("PERMISSION = \"" + VIEW_PERMISSION + "\""));
        assertTrue(source.contains("PlayerIdentity target = loaded.find(targetInput).orElse(null);"));
        assertTrue(source.contains("CommandRoute.canonicalName(command).equals(\"endersee\")"));
        assertTrue(source.contains("viewer.getScheduler().execute(plugin, () -> inventories.open("));
        assertFalse(source.contains("Bukkit.getOfflinePlayer("));
    }

    @Test
    void guiKeepsViewAndEditAuthoritySeparate() throws IOException {
        String source = normalizedSource(COORDINATOR_SOURCE);
        JsonNode metadata = pluginMetadata();

        assertTrue(source.contains("if (!viewer.hasPermission(\"" + EDIT_PERMISSION + "\"))"));
        assertTrue(source.contains("You may inspect this inventory but not edit it."));
        assertTrue(source.contains("online.getScheduler().execute(plugin, () ->"));
        assertTrue(source.contains("queueOfflineEdit(viewer, holder);"));

        JsonNode commands = metadata.path("commands");
        assertEquals(VIEW_PERMISSION, commands.path("invsee").path("permission").asText());
        assertEquals(VIEW_PERMISSION, commands.path("endersee").path("permission").asText());

        JsonNode permissions = metadata.path("permissions");
        JsonNode viewPermission = permissions.path(VIEW_PERMISSION);
        JsonNode editPermission = permissions.path(EDIT_PERMISSION);
        assertTrue(viewPermission.hasNonNull(DEFAULT_FIELD));
        assertTrue(editPermission.hasNonNull(DEFAULT_FIELD));
        assertFalse(viewPermission.path(DEFAULT_FIELD).asBoolean());
        assertFalse(editPermission.path(DEFAULT_FIELD).asBoolean());
    }

    private static String normalizedSource(Path source) throws IOException {
        return Files.readString(source).replace("\r\n", "\n");
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
