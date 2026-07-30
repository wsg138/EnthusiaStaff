package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandPermissionConfigurationTest {
    private static final Map<String, String> EXPECTED_PERMISSIONS = Map.ofEntries(
            Map.entry("estaff", "enthusiastaff.status"),
            Map.entry("punish", "enthusiastaff.punish"),
            Map.entry("ban", "enthusiastaff.punish"),
            Map.entry("mute", "enthusiastaff.punish"),
            Map.entry("warn", "enthusiastaff.punish"),
            Map.entry("kick", "enthusiastaff.punish"),
            Map.entry("ipban", "enthusiastaff.punish.ip"),
            Map.entry("removepunishment", "enthusiastaff.remove"),
            Map.entry("unban", "enthusiastaff.remove"),
            Map.entry("unmute", "enthusiastaff.remove"),
            Map.entry("removewarning", "enthusiastaff.remove"),
            Map.entry("unwarn", "enthusiastaff.remove"),
            Map.entry("reports", "enthusiastaff.reports.manage"),
            Map.entry("freeze", "enthusiastaff.freeze"),
            Map.entry("unfreeze", "enthusiastaff.freeze"),
            Map.entry("staff", "enthusiastaff.staffmode"),
            Map.entry("vanish", "enthusiastaff.vanish"),
            Map.entry("staffchat", "enthusiastaff.staffchat"),
            Map.entry("client", "enthusiastaff.client"),
            Map.entry("invsee", "enthusiastaff.inventory.view"),
            Map.entry("endersee", "enthusiastaff.inventory.view"),
            Map.entry("inspect", "enthusiastaff.inspect"),
            Map.entry("case", "enthusiastaff.case.restoreitems")
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

    @Test
    void playerReportCommandRemainsIntentionallyPublic() throws IOException {
        assertTrue(pluginMetadata().path("commands").path("report").path("permission").isMissingNode());
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
