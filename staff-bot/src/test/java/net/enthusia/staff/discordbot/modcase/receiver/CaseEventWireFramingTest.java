package net.enthusia.staff.discordbot.modcase.receiver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The offline API's real synthetic fixture must be framed as exactly one JSON value. */
class CaseEventWireFramingTest {
    private String apiFixture() throws Exception {
        try (InputStream source = getClass().getResourceAsStream(
                "/modcase/api-generated-case-events.jsonl")) {
            assertNotNull(source);
            return new String(source.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().findFirst().orElseThrow();
        }
    }

    @Test
    void exactSingleApiEnvelopeAndWhitespaceAreAccepted() throws Exception {
        String wire = apiFixture();
        assertEquals("CASE_CREATED", CaseEvent.parse(wire).eventType());
        assertEquals("CASE_CREATED", CaseEvent.parse("  " + wire + "  \n").eventType());
    }

    @Test
    void concatenatedSecondJsonValueIsRejected() throws Exception {
        String wire = apiFixture();
        assertThrows(IllegalArgumentException.class,
                () -> CaseEvent.parse(wire + "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> CaseEvent.parse(wire + " []"));
    }

    @Test
    void trailingNonJsonBytesAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> CaseEvent.parse(apiFixture() + " unexpected-trailer"));
    }
}
