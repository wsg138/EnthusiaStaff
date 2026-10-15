package net.enthusia.staff.discordbot.modcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;

class HttpCanonicalCaseGatewayTest {
    private static final String SECRET = "synthetic-only-secret-32-characters-minimum";
    private static final String ACTOR = "staff:alice";
    private static final String SCOPE = "smp";
    private static final URI ENDPOINT = URI.create("http://127.0.0.1:43210/v1/staff-case-commands");
    private static final ObjectMapper JSON = new ObjectMapper();

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
                ENDPOINT, "synthetic-staffbot", SECRET, request -> {
                    outgoing.add(request);
                    return new HttpCanonicalCaseGateway.RawResponse(200, response(command, "CLAIMED"));
                });
        assertEquals(AuthorityBackedCaseActions.TransportStatus.ACCEPTED, gateway.send(command));
        assertEquals(1, outgoing.size());
        HttpRequest request = outgoing.getFirst();
        assertEquals(ENDPOINT, request.uri());
        assertEquals("POST", request.method());
        assertEquals("synthetic-staffbot",
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
        assertFalse(body.contains(SECRET));
    }

    @Test
    void deniesMalformedOrMismatchedResponseWithoutClaimingSuccess() {
        AuthorityBackedCaseActions.Command command = command(CaseNotice.Action.CLAIM);
        List<String> responses = List.of(
                "", "{}", "{bad json}", response(command, "CLOSED"),
                response(command, "CLAIMED").replace(command.caseId().toString(), UUID.randomUUID().toString()),
                response(command, "CLAIMED").replace("\"revision\":1", "\"revision\":2"),
                response(command, "CLAIMED").replace("\"replay\":false", "\"replay\":\"false\""));
        for (String body : responses) {
            var gateway = new HttpCanonicalCaseGateway(ENDPOINT, "synthetic-staffbot", SECRET,
                    request -> new HttpCanonicalCaseGateway.RawResponse(200, body));
            assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE,
                    gateway.send(command), body);
        }
    }

    @Test
    void transportMapsFailuresAndNeverRetriesInternally() {
        var command = command(CaseNotice.Action.RESOLVE);
        int[] calls = {0};
        HttpCanonicalCaseGateway unknown = new HttpCanonicalCaseGateway(
                ENDPOINT, "synthetic-staffbot", SECRET, request -> {
                    calls[0]++;
                    throw new IOException("synthetic connection closed after uncertain send");
                });
        assertEquals(AuthorityBackedCaseActions.TransportStatus.RETRYABLE, unknown.send(command));
        assertEquals(1, calls[0]);
        for (int code : List.of(401, 403, 404, 422)) {
            var denied = new HttpCanonicalCaseGateway(ENDPOINT, "synthetic-staffbot", SECRET,
                    request -> new HttpCanonicalCaseGateway.RawResponse(code, ""));
            assertEquals(AuthorityBackedCaseActions.TransportStatus.DENIED,
                    denied.send(command));
        }
        var stale = new HttpCanonicalCaseGateway(ENDPOINT, "synthetic-staffbot", SECRET,
                request -> new HttpCanonicalCaseGateway.RawResponse(409, ""));
        assertEquals(AuthorityBackedCaseActions.TransportStatus.STALE, stale.send(command));
        var accepted = new HttpCanonicalCaseGateway(ENDPOINT, "synthetic-staffbot", SECRET,
                request -> new HttpCanonicalCaseGateway.RawResponse(200, response(command, "CLOSED")));
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
        for (String endpoint : badEndpoints) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HttpCanonicalCaseGateway(
                            URI.create(endpoint), "synthetic-staffbot", SECRET));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new HttpCanonicalCaseGateway(ENDPOINT, "synthetic-staffbot", "short"));
        assertTrue(SECRET.length() >= 32);
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
