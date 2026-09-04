package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ModerationReadApiServerBindingTest {
    @Test
    void privateReadApiUsesOnlyFixedLoopbackPort() {
        InetSocketAddress address = ModerationReadApiServer.bindAddress();

        assertEquals("127.0.0.1", address.getAddress().getHostAddress());
        assertEquals(8766, address.getPort());
    }

    @Test
    void browserReadsAllowOnlyExactStagingPanelOrigin() {
        assertTrue(ModerationReadApiServer.originAllowed(null));
        assertTrue(ModerationReadApiServer.originAllowed(List.of()));
        assertTrue(ModerationReadApiServer.originAllowed(List.of(ModerationReadApiServer.PREVIEW_ORIGIN)));
        assertTrue(ModerationReadApiServer.browserOrigin(List.of(ModerationReadApiServer.PREVIEW_ORIGIN)));

        assertFalse(ModerationReadApiServer.originAllowed(List.of("https://attacker.example")));
        assertFalse(ModerationReadApiServer.originAllowed(List.of(
                ModerationReadApiServer.PREVIEW_ORIGIN, ModerationReadApiServer.PREVIEW_ORIGIN)));
        assertFalse(ModerationReadApiServer.browserOrigin(null));
        assertFalse(ModerationReadApiServer.browserOrigin(List.of()));
    }

    @Test
    void browserPreflightRequiresExactlyTheSignedReadHeaders() {
        String exact = String.join(", ",
                "Content-Type",
                ModerationReadApiAuthenticator.TIMESTAMP_HEADER,
                ModerationReadApiAuthenticator.NONCE_HEADER,
                ModerationReadApiAuthenticator.SIGNATURE_HEADER);
        String lowercaseReordered = String.join(",",
                ModerationReadApiAuthenticator.SIGNATURE_HEADER.toLowerCase(Locale.ROOT),
                "content-type",
                ModerationReadApiAuthenticator.NONCE_HEADER.toLowerCase(Locale.ROOT),
                ModerationReadApiAuthenticator.TIMESTAMP_HEADER.toLowerCase(Locale.ROOT));

        assertTrue(ModerationReadApiServer.validPreflightHeaders(exact));
        assertTrue(ModerationReadApiServer.validPreflightHeaders(lowercaseReordered));
        assertFalse(ModerationReadApiServer.validPreflightHeaders(null));
        assertFalse(ModerationReadApiServer.validPreflightHeaders(""));
        assertFalse(ModerationReadApiServer.validPreflightHeaders("Content-Type"));
        assertFalse(ModerationReadApiServer.validPreflightHeaders(exact + ", Authorization"));
    }

    @Test
    void malformedReadJsonIsRejectedBeforeServiceDispatch() throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(new Jdk8Module());

        assertThrows(IllegalArgumentException.class, () -> ModerationReadApiServer.parseRequest(
                json, "{".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ModerationReadApiServer.parseRequest(
                json, "null".getBytes(StandardCharsets.UTF_8)));
        assertNotNull(ModerationReadApiServer.parseRequest(
                json,
                "{\"actorId\":\"1\",\"guildId\":\"2\",\"targetKey\":\"discord:3\",\"messages\":null}"
                        .getBytes(StandardCharsets.UTF_8)));
    }
}
