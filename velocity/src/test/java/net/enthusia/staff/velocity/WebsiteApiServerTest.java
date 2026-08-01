package net.enthusia.staff.velocity;

import static net.enthusia.staff.velocity.WebsiteApiTestSignatures.BEARER;
import static net.enthusia.staff.velocity.WebsiteApiTestSignatures.HMAC;
import static net.enthusia.staff.velocity.WebsiteApiTestSignatures.signedHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import org.junit.jupiter.api.Test;

final class WebsiteApiServerTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String PUBLIC_PATH = "/v1/public/punishments?limit=1";

    @Test
    void servesAuthenticatedLoopbackRequestWithHardenedHeaders() throws Exception {
        TransportStore store = new TransportStore();
        int port = freePort();
        try (WebsiteApiServer server = server(port, store);
             HttpClient client = HttpClient.newHttpClient()) {
            server.start();

            HttpResponse<String> response = client.send(
                    request(port, signedHeaders(
                            "GET",
                            PUBLIC_PATH,
                            new byte[0],
                            NOW,
                            "77bb7ba0-fb99-431c-af1d-e07339444523"
                    )),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            JsonNode body = new ObjectMapper().readTree(response.body());
            assertTrue(body.path("items").isArray());
            assertEquals(1, store.recordedNonces);
            assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
            assertEquals("nosniff", response.headers().firstValue("x-content-type-options").orElseThrow());
            assertTrue(response.headers().firstValue("x-request-id").isPresent());
        }
    }

    @Test
    void rejectsUnsignedRequestBeforeStoreAccess() throws Exception {
        TransportStore store = new TransportStore();
        int port = freePort();
        try (WebsiteApiServer server = server(port, store);
             HttpClient client = HttpClient.newHttpClient()) {
            server.start();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri(port)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(401, response.statusCode());
            assertEquals(0, store.recordedNonces);
            JsonNode body = new ObjectMapper().readTree(response.body());
            assertEquals("AUTHENTICATION_FAILED", body.path("error").path("code").textValue());
        }
    }

    @Test
    void rejectsDuplicateStartAndSupportsCleanRestart() throws Exception {
        WebsiteApiServer server = server(freePort(), new TransportStore());
        try {
            server.start();
            assertThrows(IllegalStateException.class, server::start);
            server.close();
            server.start();
        } finally {
            server.close();
        }
    }

    @Test
    void failedBindReleasesRuntimeForSafeRetry() throws Exception {
        int port;
        WebsiteApiServer server;
        try (LoopbackPortReservation blocker = LoopbackPortReservation.reserve()) {
            port = blocker.port();
            server = server(port, new TransportStore());
            assertThrows(IOException.class, server::start);
        }
        try (server) {
            server.start();
        }
    }

    @Test
    void rejectsOversizedBodyBeforeAuthenticationOrStorage() throws Exception {
        TransportStore store = new TransportStore();
        int port = freePort();
        try (WebsiteApiServer server = server(port, store);
             HttpClient client = HttpClient.newHttpClient()) {
            server.start();
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + port + "/v1/website/punishment-codes/claim"))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[1_025]))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(413, response.statusCode());
            assertEquals(0, store.recordedNonces);
            JsonNode body = new ObjectMapper().readTree(response.body());
            assertEquals("REQUEST_TOO_LARGE", body.path("error").path("code").textValue());
        }
    }

    @Test
    void configurationRequiresLoopbackAndBoundedCapacity() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebsiteApiServerConfiguration(
                        InetAddress.getByName("192.0.2.1"),
                        8_080,
                        1_024,
                        1,
                        8
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebsiteApiServerConfiguration(loopback, 8_080, 1_023, 1, 8)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebsiteApiServerConfiguration(loopback, 8_080, 1_024, 0, 8)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebsiteApiServerConfiguration(loopback, 8_080, 1_024, 1, 7)
        );
    }

    private static WebsiteApiServer server(int port, TransportStore store) {
        AuthorizationPolicy authorization = (actor, action) -> true;
        return new WebsiteApiServer(
                new WebsiteApiServerConfiguration(
                        InetAddress.getLoopbackAddress(),
                        port,
                        1_024,
                        1,
                        8
                ),
                new WebsiteApiAuthenticator(BEARER, HMAC, Duration.ofMinutes(5), store),
                new WebsiteApiRouter(
                        store,
                        authorization,
                        new SanctionChangeService(
                                authorization,
                                request -> new net.enthusia.staff.domain.sanction.SanctionChangeResult.Applied(1, false)
                        ),
                        () -> OperationalMode.ACTIVE,
                        CLOCK
                ),
                CLOCK,
                (message, failure) -> {
                    throw new AssertionError(message, failure);
                }
        );
    }

    private static HttpRequest request(int port, Headers headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(port)).GET();
        headers.forEach((name, values) -> values.forEach(value -> request.header(name, value)));
        return request.build();
    }

    private static URI uri(int port) {
        return URI.create("http://127.0.0.1:" + port + PUBLIC_PATH);
    }

    private static int freePort() throws IOException {
        try (LoopbackPortReservation reservation = LoopbackPortReservation.reserve()) {
            return reservation.port();
        }
    }

    private static final class LoopbackPortReservation implements AutoCloseable {
        private final ServerSocket socket;

        private LoopbackPortReservation() throws IOException {
            socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        }

        private static LoopbackPortReservation reserve() throws IOException {
            return new LoopbackPortReservation();
        }

        private int port() {
            return socket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class TransportStore extends WebsiteModerationStoreStub {
        private int recordedNonces;

        @Override
        public boolean recordApiNonce(byte[] nonceHash, Instant expiresAt) {
            recordedNonces++;
            return true;
        }

        @Override
        public PublicPunishmentPage listPublic(
                net.enthusia.staff.domain.website.PublicPunishmentFilter filter,
                Optional<String> cursor,
                int limit,
                Instant now
        ) {
            return new PublicPunishmentPage(List.of(), Optional.empty());
        }
    }
}
