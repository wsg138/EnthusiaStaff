package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class HttpStaffAuthorityClientTest {
    private static final String SECRET = Character.toString('s').repeat(40);

    @Test
    void preservesAuthorityResourcePathWhenAddingPlayerQuery() throws IOException {
        UUID playerId = UUID.fromString("0f48cf03-f319-41e8-981f-4d0e765b5b49");
        AtomicReference<URI> requestUri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1/staff-rank", exchange -> {
            requestUri.set(exchange.getRequestURI());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = StaffRank.MOD.name().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:%d/v1/staff-rank".formatted(server.getAddress().getPort()));
            HttpStaffAuthorityClient client = new HttpStaffAuthorityClient(endpoint, SECRET);

            assertEquals(Optional.of(StaffRank.MOD), client.rank(playerId));
            assertEquals("/v1/staff-rank", requestUri.get().getPath());
            assertEquals("player=" + playerId, requestUri.get().getRawQuery());
            assertEquals("Bearer " + SECRET, authorization.get());
        } finally {
            server.stop(0);
        }
    }
}
