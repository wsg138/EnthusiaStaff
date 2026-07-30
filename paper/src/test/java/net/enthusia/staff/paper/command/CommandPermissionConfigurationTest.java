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
    private static final String PUNISH_PERMISSION = "enthusiastaff.punish";
    private static final String REMOVE_PERMISSION = "enthusiastaff.remove";
    private static final String FREEZE_PERMISSION = "enthusiastaff.freeze";
    private static final String INVENTORY_VIEW_PERMISSION = "enthusiastaff.inventory.view";
    private static final Map<String, String> EXPECTED_PERMISSIONS = Map.ofEntries(
            Map.entry("estaff", "enthusiastaff.status"),
            Map.entry("punish", PUNISH_PERMISSION),
            Map.entry("ban", PUNISH_PERMISSION),
            Map.entry("mute", PUNISH_PERMISSION),
            Map.entry("warn", PUNISH_PERMISSION),
            Map.entry("kick", PUNISH_PERMISSION),
            Map.entry("ipban", "enthusiastaff.punish.ip"),
            Map.entry("removepunishment", REMOVE_PERMISSION),
            Map.entry("unban", REMOVE_PERMISSION),
            Map.entry("unmute", REMOVE_PERMISSION),
            Map.entry("removewarning", REMOVE_PERMISSION),
            Map.entry("unwarn", REMOVE_PERMISSION),
            Map.entry("reports", "enthusiastaff.reports.manage"),
            Map.entry("freeze", FREEZE_PERMISSION),
            Map.entry("unfreeze", FREEZE_PERMISSION),
            Map.entry("staff", "enthusiastaff.staffmode"),
            Map.entry("vanish", "enthusiastaff.vanish"),
            Map.entry("staffchat", "enthusiastaff.staffchat"),
            Map.entry("client", "enthusiastaff.client"),
            Map.entry("invsee", INVENTORY_VIEW_PERMISSION),
            Map.entry("endersee", INVENTORY_VIEW_PERMISSION),
            Map.entry("inspect", "enthusiastaff.inspect"),
            Map.entry("case", "enthusiastaff.case.restoreitems")
    );

    @Test
    void staffOnlyCommandsRetainTheirOuterPermissionBoundary() throws IOException {
        JsonNode commands = pluginMetadata().path("commands");

        for (Map.Entry<String, String> expected : EXPECTED_PERMISSIONS.entrySet()) {
            assertEquals(
                    expected.getValue(),
                    commands.path(expected.getKey()).path("permission").asText(),
                    expected.getKey()
            );
        }
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
