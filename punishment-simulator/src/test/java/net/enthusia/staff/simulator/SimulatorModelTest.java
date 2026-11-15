package net.enthusia.staff.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulatorModelTest {
    private final ObjectMapper json = new ObjectMapper();
    private SimulatorModel simulator;

    @BeforeEach
    void setUp() {
        simulator = new SimulatorModel(Path.of("../paper/src/main/resources/reason-policies.yml"));
    }

    @Test
    void simulatorAndProductionPolicyParserRunOnJava21() throws Exception {
        for (Class<?> type : java.util.List.of(SimulatorMain.class,
                net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader.class)) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var stream = type.getResourceAsStream(resource)) {
                if (stream == null) throw new AssertionError("Missing class " + resource);
                byte[] prefix = stream.readNBytes(8);
                int majorVersion = ((prefix[6] & 0xff) << 8) | (prefix[7] & 0xff);
                assertEquals(65, majorVersion, type.getName() + " must use Java 21 bytecode");
            }
        }
    }

    @Test
    void evaluatesFirstOffenseAndRelatedRepeatUsingRealEngine() throws Exception {
        assertEquals("Warning", decision("[]").path("selected").path("label").asText());
        JsonNode result = decision("[{\"reasonId\":\"spam.low-level\",\"daysAgo\":7}]");
        assertEquals(2, result.path("effectiveOrdinal").asInt());
        assertEquals(1, result.path("recencyBonus").asInt());
        assertEquals("6 hour mute", result.path("selected").path("label").asText());
    }

    @Test
    void severityDecayAndUnrelatedHistoryMatchProductionCalculation() throws Exception {
        var severe = decision("[{\"reasonId\":\"spam.message-flooding\",\"daysAgo\":7}]");
        assertEquals(3, severe.path("effectiveOrdinal").asInt());
        var old = decision("[{\"reasonId\":\"spam.low-level\",\"daysAgo\":180}]");
        assertEquals(0, old.path("effectiveOrdinal").asInt());
        var unrelated = decision("[{\"reasonId\":\"hate.general-toxicity\",\"daysAgo\":7}]");
        assertEquals(0, unrelated.path("effectiveOrdinal").asInt());
        assertEquals(1, unrelated.path("ignoredCount").asInt());
    }

    @Test
    void ignoredAndOverturnedHistoryNeverEscalates() throws Exception {
        JsonNode result = decision("[{\"reasonId\":\"spam.low-level\",\"daysAgo\":7,\"overturned\":true},"
                + "{\"reasonId\":\"spam.message-flooding\",\"daysAgo\":3,\"contributes\":false}]");
        assertEquals(0, result.path("effectiveOrdinal").asInt());
        assertEquals(2, result.path("ignoredCount").asInt());
    }

    @Test
    void rejectsInventedReasonsAndInvalidHistory() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> simulator.evaluate(json.readTree(
                        "{\"reasonId\":\"not.a.reason\",\"asOf\":\"2026-10-13\",\"history\":[]}")));
        assertThrows(IllegalArgumentException.class,
                () -> decision("[{\"reasonId\":\"spam.low-level\",\"daysAgo\":-5}]"));
    }

    private JsonNode decision(String history) throws Exception {
        return json.valueToTree(simulator.evaluate(json.readTree(
                "{\"reasonId\":\"spam.low-level\",\"asOf\":\"2026-10-13\",\"history\":"
                        + history + "}")));
    }
}
