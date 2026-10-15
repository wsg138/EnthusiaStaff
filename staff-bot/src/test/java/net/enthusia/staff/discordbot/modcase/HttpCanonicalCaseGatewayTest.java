package net.enthusia.staff.discordbot.modcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;

class HttpCanonicalCaseGatewayTest {
    private static final String SECRET = randomTestSecret();
    private static final String CLIENT_ID = "synthetic-staffbot";
    private static final String STATE_CLAIMED = "CLAIMED";
    private static final String ACTOR = "staff:alice";
    private static final String SCOPE = "smp";
    private static final URI ENDPOINT = URI.create("http://127.0.0.1:43210/v1/staff-case-commands");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SYNTHETIC_SIGNATURE = "a".repeat(64);
    private static final HttpCanonicalCaseGateway.AuthorityProofSource PROOF_SOURCE =
            command -> new HttpCanonicalCaseGateway.ActorProof(
                    1791993600L, 1791993630L, SYNTHETIC_SIGNATURE);

    private AuthorityBackedCaseActions.Command command(CaseNotice.Action action) {
        return new AuthorityBackedCaseActions.Command(CaseNotice.Origin.MINECRAFT, SCOPE,
                UUID.fromString("3b0d5bf6-355a-48b5-a701-9c0236d7fd99"), 0,
                ACTOR, action, "command:synthetic:0001");
    }

    private String response(AuthorityBackedCaseActions.Command command, String state) {
        return "{\"case_id\":\"" + command.caseId()
                + "\",\"revision\":1,\"state\":\"" + state + "\",\"replay\":false}";
    }

    @Test
    void sendsBoundedPrivateCommandToExactEndpointAndAcceptsOnlyMatchingResponse() throws Exception {
        AuthorityBackedCaseActions.Command command = command(CaseNotice.Action.CLAIM);
        List<HttpRequest> outgoing = new ArrayList<>();
        HttpCanonicalCaseGateway gateway = new HttpCanonicalCaseGateway(
                ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE, request -> {
                    outgoing.add(request);
                    return raw(200, response(command, STATE_CLAIMED));
                });
        assertEquals(AuthorityBackedCaseActions.TransportStatus.ACCEPTED, gateway.send(command));
        assertEquals(1, outgoing.size());
        HttpRequest request = outgoing.getFirst();
        assertEquals(ENDPOINT, request.uri());
        assertEquals("POST", request.method());
        assertEquals(CLIENT_ID,
                request.headers().firstValue("X-Client-Id").orElseThrow());
        assertEquals("Bearer " + SECRET,
                request.headers().firstValue("Authorization").orElseThrow());
        String body = requestBody(request);
        JsonNode payload = JSON.readTree(body);
        assertEquals(command.caseId().toString(), payload.path("case_id").asText());
        assertEquals("minecraft", payload.path("origin_platform").asText());
        assertEquals(SCOPE, payload.path("scope_id").asText());
        assertEquals(ACTOR, payload.path("staff_actor_id").asText());
        assertEquals("command:synthetic:0001", payload.path("command_id").asText());
        assertEquals(0, payload.path("expected_revision").asInt());
        assertEquals("CLAIM", payload.path("action").asText());
        assertEquals(1791993600L, payload.path("actor_proof_issued_at").asLong());
        assertEquals(1791993630L, payload.path("actor_proof_expires_at").asLong());
        assertEquals(SYNTHETIC_SIGNATURE, payload.path("actor_proof_signature").asText());
        assertFalse(body.contains(SECRET));
    }

    @Test
    void deniesMalformedOrMismatchedResponseWithoutClaimingSuccess() {
        AuthorityBackedCaseActions.Command command = command(CaseNotice.Action.CLAIM);
        List<String> responses = List.of(
                "", "{}", "{bad json}", response(command, "CLOSED"),
                response(command, STATE_CLAIMED).replace(command.caseId().toString(), UUID.randomUUID().toString()),
                response(command, STATE_CLAIMED).replace("\"revision\":1", "\"revision\":2"),
                response(command, STATE_CLAIMED).replace("\"replay\":false", "\"replay\":\"false\""));
        assertEquals(7, responses.size());
        responses.forEach(body -> {
            var gateway = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                    request -> raw(200, body));
            assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                    gateway.send(command), body);
        });
    }

    @Test
    void transportMapsFailuresAndNeverRetriesInternally() {
        var command = command(CaseNotice.Action.RESOLVE);
        int[] calls = {0};
        HttpCanonicalCaseGateway unknown = new HttpCanonicalCaseGateway(
                ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE, request -> {
                    calls[0]++;
                    throw new IOException("synthetic connection closed after uncertain send");
                });
        assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE, unknown.send(command));
        assertEquals(1, calls[0]);
        List.of(401, 403, 404, 422).forEach(code -> {
            var denied = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                    request -> raw(code, ""));
            assertEquals(AuthorityBackedCaseActions.TransportStatus.DENIED,
                    denied.send(command));
        });
        var stale = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                request -> raw(409, ""));
        assertEquals(AuthorityBackedCaseActions.TransportStatus.STALE, stale.send(command));
        var accepted = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                request -> raw(200, response(command, "CLOSED")));
        assertEquals(AuthorityBackedCaseActions.TransportStatus.ACCEPTED, accepted.send(command));
    }

    @Test
    void refusesUnsafeTransportAndWeakSecretConfigurations() {
        List<String> badEndpoints = List.of(
                "http://example.com/v1/staff-case-commands",
                "https://example.com/other-path",
                "https://user:password@example.com/v1/staff-case-commands",
                "http://127.0.0.1:43210/v1/staff-case-commands?redirect=true",
                "file:///v1/staff-case-commands");
        badEndpoints.forEach(endpoint -> assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway(
                        URI.create(endpoint), CLIENT_ID, SECRET, PROOF_SOURCE)));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, "short", PROOF_SOURCE));
        assertTrue(SECRET.length() >= 32);
    }

    @Test
    void missingOrUnavailableProofNeverMakesAnHttpRequest() {
        var command = command(CaseNotice.Action.CLAIM);
        int[] transmissions = {0};
        HttpCanonicalCaseGateway.Sender sender = request -> {
            transmissions[0]++;
            throw new AssertionError("proof missing, request must not be sent");
        };
        HttpCanonicalCaseGateway missing = new HttpCanonicalCaseGateway(
                ENDPOINT, CLIENT_ID, SECRET, ignored -> null, sender);
        assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                missing.send(command));
        HttpCanonicalCaseGateway unavailable = new HttpCanonicalCaseGateway(
                ENDPOINT, CLIENT_ID, SECRET, ignored -> {
                    throw new IOException("synthetic Paper role authority unavailable");
                }, sender);
        assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                unavailable.send(command));
        assertEquals(0, transmissions[0]);
    }

    @Test
    void malformedProofCannotBeCreatedAndProofSourceMustBeProvided() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway.ActorProof(4, 3, SYNTHETIC_SIGNATURE));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway.ActorProof(4, 80, SYNTHETIC_SIGNATURE));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway.ActorProof(4, 15, "not-a-mac"));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, null));
    }

    @Test
    void exactly4096Utf8BytesAreAccepted() {
        var command = command(CaseNotice.Action.CLAIM);
        String reply = response(command, STATE_CLAIMED);
        String padded = reply + " ".repeat(4096 - reply.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(4096, padded.getBytes(StandardCharsets.UTF_8).length);
        var gateway = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                request -> raw(200, padded));
        assertEquals(AuthorityBackedCaseActions.TransportStatus.ACCEPTED, gateway.send(command));
    }

    @Test
    void oversizedAsciiAndMultibyteStreamsStopAt4097Bytes() {
        var command = command(CaseNotice.Action.CLAIM);
        List<String> replies = List.of(response(command, STATE_CLAIMED)
                + " ".repeat(4097 - response(command, STATE_CLAIMED).length()),
                response(command, STATE_CLAIMED) + "😀".repeat(1100));
        replies.forEach(reply -> {
            assertTrue(reply.getBytes(StandardCharsets.UTF_8).length > 4096);
            TrackingStream stream = new TrackingStream(reply.getBytes(StandardCharsets.UTF_8), 23);
            AtomicInteger sends = new AtomicInteger();
            var gateway = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                    request -> {
                        sends.incrementAndGet();
                        return new HttpCanonicalCaseGateway.RawResponse(200, stream);
                    });
            assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE, gateway.send(command));
            assertEquals(4097, stream.bytesRead);
            assertTrue(stream.closed);
            assertEquals(1, sends.get());
        });
        assertTrue(replies.get(1).length() < 4096, "UTF-16 length misses UTF-8 overflow");
    }

    @Test
    void malformedUtf8AndMidBodyIoErrorAreRetryable() throws IOException {
        var command = command(CaseNotice.Action.CLAIM);
        byte[] malformed = (response(command, STATE_CLAIMED) + " ").getBytes(StandardCharsets.UTF_8);
        malformed[malformed.length - 1] = (byte) 0xc3;
        TrackingStream invalid = new TrackingStream(malformed, 3);
        var bad = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                request -> new HttpCanonicalCaseGateway.RawResponse(200, invalid));
        assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE, bad.send(command));
        assertTrue(invalid.closed);
        try (InputStream broken = new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException("synthetic body disconnect");
            }
        }) {
            var disconnected = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                    request -> new HttpCanonicalCaseGateway.RawResponse(200, broken));
            assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                    disconnected.send(command));
        }
    }

    @Test
    void timeoutsAndInterruptionDoNotCauseResends() {
        var command = command(CaseNotice.Action.CLAIM);
        AtomicInteger attempts = new AtomicInteger();
        var timeout = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE, request -> {
            attempts.incrementAndGet();
            throw new HttpTimeoutException("synthetic timeout");
        });
        assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE, timeout.send(command));
        assertEquals(1, attempts.get());
        var interrupted = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE, request -> {
            attempts.incrementAndGet();
            throw new InterruptedException("synthetic interruption");
        });
        try {
            assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE, interrupted.send(command));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(2, attempts.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void nonSuccessHttpStatusesCloseBodiesWithoutReadingThem() {
        var command = command(CaseNotice.Action.CLAIM);
        for (int code : List.of(409, 401, 403, 404, 422, 503)) {
            assertNonSuccessBodyClosed(code, command);
        }
    }

    private static void assertNonSuccessBodyClosed(
            int code, AuthorityBackedCaseActions.Command command) {
        TrackingStream stream = new TrackingStream(new byte[100000], 128);
        var gateway = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                request -> new HttpCanonicalCaseGateway.RawResponse(code, stream));
        var expected = code == 409 ? AuthorityBackedCaseActions.TransportStatus.STALE
                : code == 503 ? AuthorityBackedCaseActions.TransportStatus.RETRYABLE
                : AuthorityBackedCaseActions.TransportStatus.DENIED;
        assertEquals(expected, gateway.send(command));
        assertEquals(0, stream.bytesRead);
        assertTrue(stream.closed);
    }

    @Test
    void loopbackChunkedResponseIsBoundedAndHasOnePost() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger posts = new AtomicInteger();
        server.createContext("/v1/staff-case-commands", exchange -> {
            posts.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[512];
            Arrays.fill(chunk, (byte) 'x');
            try (OutputStream output = exchange.getResponseBody()) {
                for (int i = 0; i < 256; i++) {
                    output.write(chunk);
                }
            } catch (IOException expectedAfterCancellation) {
                // Client canceled unread chunks; release the HTTP exchange.
                exchange.close();
                return;
            }
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/staff-case-commands");
            var gateway = new HttpCanonicalCaseGateway(endpoint, CLIENT_ID, SECRET, PROOF_SOURCE);
            assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                    gateway.send(command(CaseNotice.Action.CLAIM)));
            assertEquals(1, posts.get());
        } finally {
            server.stop(0);
        }
    }

    private static HttpCanonicalCaseGateway.RawResponse raw(int code, String content) {
        return new HttpCanonicalCaseGateway.RawResponse(code,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class TrackingStream extends InputStream {
        private final byte[] bytes;
        private final int chunkSize;
        private int bytesRead;
        private boolean closed;

        private TrackingStream(byte[] bytes, int chunkSize) {
            this.bytes = bytes;
            this.chunkSize = chunkSize;
        }

        @Override
        public int read() {
            return bytesRead == bytes.length ? -1 : bytes[bytesRead++] & 0xff;
        }

        @Override
        public int read(byte[] destination, int offset, int length) {
            if (bytesRead == bytes.length) {
                return -1;
            }
            int count = Math.min(Math.min(length, chunkSize), bytes.length - bytesRead);
            System.arraycopy(bytes, bytesRead, destination, offset, count);
            bytesRead += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static String randomTestSecret() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String requestBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        List<ByteBuffer> chunks = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer item) { chunks.add(item); }
            @Override public void onError(Throwable throwable) { throw new IllegalStateException(throwable); }
            @Override public void onComplete() { }
        });
        int size = chunks.stream().mapToInt(ByteBuffer::remaining).sum();
        byte[] bytes = new byte[size];
        ByteBuffer dest = ByteBuffer.wrap(bytes);
        chunks.forEach(dest::put);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
