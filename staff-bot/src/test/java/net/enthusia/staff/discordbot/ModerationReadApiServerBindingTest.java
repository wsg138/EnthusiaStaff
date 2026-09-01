package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModerationReadApiServerBindingTest {
    @Test
    void privateReadApiUsesOnlyFixedLoopbackPort() {
        InetSocketAddress address = ModerationReadApiServer.bindAddress();

        assertEquals("127.0.0.1", address.getAddress().getHostAddress());
        assertEquals(8766, address.getPort());
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
