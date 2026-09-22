package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import net.enthusia.staff.paper.staff.StaffToolSettings;

final class StaffToolConfigurationParser {
    StaffToolSettings parse(JsonNode root, List<String> errors) {
        JsonNode staffTools = ConfigurationNodes.optionalMapping(
                root, "staff-tools", "staff-tools", errors
        );
        JsonNode randomTeleport = ConfigurationNodes.optionalMapping(
                staffTools, "random-teleport", "staff-tools.random-teleport", errors
        );
        JsonNode cooldowns = ConfigurationNodes.optionalMapping(
                staffTools, "cooldowns", "staff-tools.cooldowns", errors
        );
        StaffToolSettings defaults = StaffToolSettings.defaults();
        return new StaffToolSettings(
                ConfigurationNodes.textSet(
                        randomTeleport,
                        "disabled-servers",
                        "staff-tools.random-teleport.disabled-servers",
                        errors
                ),
                ConfigurationNodes.textSet(
                        randomTeleport,
                        "disabled-worlds",
                        "staff-tools.random-teleport.disabled-worlds",
                        errors
                ),
                Duration.ofMillis(ConfigurationNodes.boundedLong(
                        cooldowns,
                        "random-teleport-millis",
                        "staff-tools.cooldowns.random-teleport-millis",
                        defaults.randomCooldown().toMillis(),
                        0L,
                        60_000L,
                        errors
                )),
                Duration.ofMillis(ConfigurationNodes.boundedLong(
                        cooldowns,
                        "target-tool-millis",
                        "staff-tools.cooldowns.target-tool-millis",
                        defaults.targetCooldown().toMillis(),
                        0L,
                        60_000L,
                        errors
                )),
                Duration.ofMillis(ConfigurationNodes.boundedLong(
                        cooldowns,
                        "toggle-tool-millis",
                        "staff-tools.cooldowns.toggle-tool-millis",
                        defaults.toggleCooldown().toMillis(),
                        0L,
                        60_000L,
                        errors
                )),
                Duration.ofMillis(ConfigurationNodes.boundedLong(
                        cooldowns,
                        "menu-millis",
                        "staff-tools.cooldowns.menu-millis",
                        defaults.menuCooldown().toMillis(),
                        0L,
                        60_000L,
                        errors
                ))
        );
    }
}
