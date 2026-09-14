package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpRoleEligibilityClientTest {
    private static final String CREDENTIAL = "s".repeat(40);
    private static final UUID PLAYER = UUID.fromString("0f48cf03-f319-41e8-981f-4d0e765b5b49");

    @Test
    void loopbackRoleEligibilityUsesDedicatedResourceAndBearerAuth() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1/role-eligibility", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "helper\nmod".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            HttpStaffAuthorityClient client = client(server);

            assertEquals(Set.of("helper", "mod"), client.groups(PLAYER));
            assertEquals("Bearer " + CREDENTIAL, authorization.get());
            assertEquals("player=" + PLAYER, query.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedRoleEligibilityFailsClosed() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1/role-eligibility", exchange -> {
            byte[] body = "valid\nNOT VALID".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertThrows(StaffAuthorityClient.UnavailableException.class, () -> client(server).groups(PLAYER));
        } finally {
            server.stop(0);
        }
    }

    private static HttpStaffAuthorityClient client(HttpServer server) {
        URI endpoint = URI.create("http://127.0.0.1:%d/v1/staff-rank".formatted(server.getAddress().getPort()));
        return new HttpStaffAuthorityClient(
                endpoint,
                CREDENTIAL,
                StaffModerationConfiguration.AuthorityTransport.LOOPBACK
        );
    }
}
