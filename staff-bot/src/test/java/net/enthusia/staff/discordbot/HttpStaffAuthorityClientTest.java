package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;
import org.junit.jupiter.api.Test;

class HttpStaffAuthorityClientTest {
    private static final String CREDENTIAL = Character.toString('s').repeat(40);

    @Test
    void loopbackTransportPreservesBearerAuthorityResourcePath() throws IOException {
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
            URI endpoint = URI.create(
                    "http://127.0.0.1:%d/v1/staff-rank".formatted(server.getAddress().getPort()));
            HttpStaffAuthorityClient client = new HttpStaffAuthorityClient(
                    endpoint,
                    CREDENTIAL,
                    StaffModerationConfiguration.AuthorityTransport.LOOPBACK);

            assertEquals(Optional.of(StaffRank.MOD), client.rank(playerId));
            assertEquals("/v1/staff-rank", requestUri.get().getPath());
            assertEquals("player=" + playerId, requestUri.get().getRawQuery());
            assertEquals("Bearer " + CREDENTIAL, authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void privateSplitTransportSignsRequestAndRequiresSignedResponse() throws IOException {
        UUID playerId = UUID.fromString("0f48cf03-f319-41e8-981f-4d0e765b5b49");
        AtomicReference<StaffAuthorityHttpSigning.Verification> verification = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1/staff-rank", exchange -> {
            String target = exchange.getRequestURI().getRawPath()
                    + "?" + exchange.getRequestURI().getRawQuery();
            String nonce = exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.NONCE_HEADER);
            verification.set(StaffAuthorityHttpSigning.verifyRequest(
                    CREDENTIAL,
                    exchange.getRequestMethod(),
                    target,
                    exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.TIMESTAMP_HEADER),
                    nonce,
                    exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.SIGNATURE_HEADER),
                    Clock.systemUTC()
            ));
            String body = StaffRank.ADMIN.name();
            exchange.getResponseHeaders().set(
                    StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER,
                    StaffAuthorityHttpSigning.signResponse(CREDENTIAL, nonce, 200, body));
            byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, encoded.length);
            exchange.getResponseBody().write(encoded);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create(
                    "http://127.0.0.1:%d/v1/staff-rank".formatted(server.getAddress().getPort()));
            HttpStaffAuthorityClient client = new HttpStaffAuthorityClient(
                    endpoint,
                    CREDENTIAL,
                    StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT);

            assertEquals(Optional.of(StaffRank.ADMIN), client.rank(playerId));
            assertEquals(StaffAuthorityHttpSigning.Verification.ACCEPTED, verification.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void privateSplitTransportRejectsUnsignedResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/v1/staff-rank", exchange -> {
            byte[] body = StaffRank.MOD.name().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create(
                    "http://127.0.0.1:%d/v1/staff-rank".formatted(server.getAddress().getPort()));
            HttpStaffAuthorityClient client = new HttpStaffAuthorityClient(
                    endpoint,
                    CREDENTIAL,
                    StaffModerationConfiguration.AuthorityTransport.BLOOM_PRIVATE_SPLIT);

            assertThrows(
                    StaffAuthorityClient.UnavailableException.class,
                    () -> client.rank(UUID.fromString("0f48cf03-f319-41e8-981f-4d0e765b5b49")));
        } finally {
            server.stop(0);
        }
    }
}
