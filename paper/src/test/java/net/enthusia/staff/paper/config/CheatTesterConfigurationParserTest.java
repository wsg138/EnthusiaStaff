package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.paper.tester.CheatTesterSettings;
import org.junit.jupiter.api.Test;

class CheatTesterConfigurationParserTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CheatTesterConfigurationParser parser = new CheatTesterConfigurationParser();

    @Test
    void parsesSafeExplicitTesterConfiguration() throws Exception {
        List<String> errors = new ArrayList<>();
        JsonNode root = json.readTree("""
                {"staff-tools":{"cheat-tester":{
                  "timeout-millis":5000,
                  "maximum-active-global":6,
                  "maximum-active-per-staff":2,
                  "fake-entity-distance":4.0,
                  "velocity":{"horizontal":0.8,"vertical":0.4},
                  "no-fall":{"vertical":0.6},
                  "probe-ticks":80
                }}}
                """);

        CheatTesterSettings settings = parser.parse(root, errors);

        assertTrue(errors.isEmpty(), () -> "Unexpected validation errors: " + errors);
        assertEquals(5_000L, settings.sessionTimeout().toMillis());
        assertEquals(6, settings.maximumActiveGlobal());
        assertEquals(2, settings.maximumActivePerStaff());
        assertEquals(4.0D, settings.fakeEntityDistance());
        assertEquals(0.8D, settings.velocityHorizontal());
        assertEquals(0.4D, settings.velocityVertical());
        assertEquals(0.6D, settings.noFallVertical());
        assertEquals(80, settings.probeTicks());
    }

    @Test
    void rejectsUnknownKeysAtEveryTesterMappingLevel() throws Exception {
        List<String> errors = new ArrayList<>();
        JsonNode root = json.readTree("""
                {"staff-tools":{"cheat-tester":{
                  "unknown-root":true,
                  "velocity":{"horizontal":0.75,"vertical":0.3,"unknown-velocity":1},
                  "no-fall":{"vertical":0.7,"unknown-no-fall":1}
                }}}
                """);

        parser.parse(root, errors);

        assertTrue(errors.contains("staff-tools.cheat-tester contains unknown key unknown-root"));
        assertTrue(errors.contains("staff-tools.cheat-tester.velocity contains unknown key unknown-velocity"));
        assertTrue(errors.contains("staff-tools.cheat-tester.no-fall contains unknown key unknown-no-fall"));
    }

    @Test
    void reportsCrossFieldLimitViolationAndUsesSafeFallback() throws Exception {
        List<String> errors = new ArrayList<>();
        JsonNode root = json.readTree("""
                {"staff-tools":{"cheat-tester":{
                  "maximum-active-global":2,
                  "maximum-active-per-staff":3
                }}}
                """);

        CheatTesterSettings settings = parser.parse(root, errors);

        assertTrue(errors.contains(
                "staff-tools.cheat-tester.maximum-active-per-staff must not exceed maximum-active-global"
        ));
        assertEquals(2, settings.maximumActiveGlobal());
        assertEquals(2, settings.maximumActivePerStaff());
    }
}
