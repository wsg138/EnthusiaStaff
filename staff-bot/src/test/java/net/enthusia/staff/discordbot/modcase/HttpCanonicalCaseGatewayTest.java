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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
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
                    return new HttpCanonicalCaseGateway.RawResponse(200, response(command, STATE_CLAIMED));
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
                    request -> new HttpCanonicalCaseGateway.RawResponse(200, body));
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
                    request -> new HttpCanonicalCaseGateway.RawResponse(code, ""));
            assertEquals(AuthorityBackedCaseActions.TransportStatus.DENIED,
                    denied.send(command));
        });
        var stale = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
                request -> new HttpCanonicalCaseGateway.RawResponse(409, ""));
        assertEquals(AuthorityBackedCaseActions.TransportStatus.STALE, stale.send(command));
        var accepted = new HttpCanonicalCaseGateway(ENDPOINT, CLIENT_ID, SECRET, PROOF_SOURCE,
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
