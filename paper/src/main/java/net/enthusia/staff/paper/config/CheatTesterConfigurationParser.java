package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import net.enthusia.staff.paper.tester.CheatTesterSettings;

final class CheatTesterConfigurationParser {
    private static final String ROOT = "staff-tools.cheat-tester";

    CheatTesterSettings parse(JsonNode root, List<String> errors) {
        CheatTesterSettings defaults = CheatTesterSettings.defaults();
        JsonNode staffTools = ConfigurationNodes.optionalMapping(root, "staff-tools", "staff-tools", errors);
        JsonNode tester = ConfigurationNodes.optionalMapping(staffTools, "cheat-tester", ROOT, errors);
        Limits limits = parseLimits(tester, defaults, errors);
        Velocity velocity = parseVelocity(tester, defaults, errors);
        return new CheatTesterSettings(
                parseTimeout(tester, defaults, errors),
                limits.global(),
                limits.perStaff(),
                boundedDouble(
                        tester,
                        "fake-entity-distance",
                        ROOT + ".fake-entity-distance",
                        defaults.fakeEntityDistance(),
                        1.0D,
                        8.0D,
                        errors
                ),
                velocity.horizontal(),
                velocity.vertical(),
                parseNoFall(tester, defaults, errors),
                ConfigurationNodes.boundedInteger(
                        tester,
                        "probe-ticks",
                        ROOT + ".probe-ticks",
                        defaults.probeTicks(),
                        10,
                        300,
                        errors
                )
        );
    }

    private static Limits parseLimits(JsonNode tester, CheatTesterSettings defaults, List<String> errors) {
        int global = ConfigurationNodes.boundedInteger(
                tester,
                "maximum-active-global",
                ROOT + ".maximum-active-global",
                defaults.maximumActiveGlobal(),
                1,
                32,
                errors
        );
        int perStaff = ConfigurationNodes.boundedInteger(
                tester,
                "maximum-active-per-staff",
                ROOT + ".maximum-active-per-staff",
                defaults.maximumActivePerStaff(),
                1,
                32,
                errors
        );
        if (perStaff <= global) {
            return new Limits(global, perStaff);
        }
        errors.add(ROOT + ".maximum-active-per-staff must not exceed maximum-active-global");
        return new Limits(global, Math.min(defaults.maximumActivePerStaff(), global));
    }

    private static Duration parseTimeout(JsonNode tester, CheatTesterSettings defaults, List<String> errors) {
        long timeout = ConfigurationNodes.boundedLong(
                tester,
                "timeout-millis",
                ROOT + ".timeout-millis",
                defaults.sessionTimeout().toMillis(),
                1_000,
                15_000,
                errors
        );
        return Duration.ofMillis(timeout);
    }

    private static Velocity parseVelocity(JsonNode tester, CheatTesterSettings defaults, List<String> errors) {
        String root = ROOT + ".velocity";
        JsonNode velocity = ConfigurationNodes.optionalMapping(tester, "velocity", root, errors);
        double horizontal = boundedDouble(
                velocity,
                "horizontal",
                root + ".horizontal",
                defaults.velocityHorizontal(),
                0.0D,
                2.0D,
                errors
        );
        double vertical = boundedDouble(
                velocity,
                "vertical",
                root + ".vertical",
                defaults.velocityVertical(),
                0.0D,
                2.0D,
                errors
        );
        return new Velocity(horizontal, vertical);
    }

    private static double parseNoFall(JsonNode tester, CheatTesterSettings defaults, List<String> errors) {
        String root = ROOT + ".no-fall";
        JsonNode noFall = ConfigurationNodes.optionalMapping(tester, "no-fall", root, errors);
        return boundedDouble(
                noFall,
                "vertical",
                root + ".vertical",
                defaults.noFallVertical(),
                0.1D,
                2.0D,
                errors
        );
    }

    private static double boundedDouble(
            JsonNode node,
            String key,
            String path,
            double fallback,
            double minimum,
            double maximum,
            List<String> errors
    ) {
        return ConfigurationNodes.boundedDouble(node, key, path, fallback, minimum, maximum, errors);
    }

    private record Limits(int global, int perStaff) {
    }

    private record Velocity(double horizontal, double vertical) {
    }
}
