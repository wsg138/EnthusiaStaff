package net.enthusia.staff.discordbot.modcase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;

/**
 * Single-attempt, opt-in StaffBot HTTP transport for the draft Moderation API case endpoint.
 * Not registered with JDA, Paper, staff UI or application bootstrap.
 */
public final class HttpCanonicalCaseGateway implements AuthorityBackedCaseActions.Gateway {
    private static final String PATH = "/v1/staff-case-commands";
    private static final String SCHEME_HTTP = "http";
    private static final int MAX_RESPONSE_BYTES = 4096;
    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;
    private static final Set<Integer> DENIED_STATUSES = Set.of(401, 403, 404, 422);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI endpoint;
    private final String clientId;
    private final String bearerSecret;
    private final Sender sender;
    private final AuthorityProofSource proofSource;

    /**
     * Only the independent trusted role authority may mint this proof.
     * StaffBot must never have the HMAC signing key.
     */
    @FunctionalInterface
    public interface AuthorityProofSource {
        ActorProof issue(AuthorityBackedCaseActions.Command command)
                throws IOException, InterruptedException;
    }

    public record ActorProof(long issuedAt, long expiresAt, String signature) {
        public ActorProof {
            if (issuedAt < 1 || expiresAt <= issuedAt || expiresAt - issuedAt > 60
                    || signature == null || !signature.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("invalid independent actor proof envelope");
            }
        }
    }

    @FunctionalInterface
    interface Sender {
        RawResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    record RawResponse(int status, InputStream body) {
        RawResponse {
            Objects.requireNonNull(body, "HTTP response stream");
        }
    }

    public HttpCanonicalCaseGateway(
            URI endpoint, String clientId, String bearerSecret, AuthorityProofSource proofSource) {
        this(endpoint, clientId, bearerSecret, proofSource, defaultSender());
    }

    HttpCanonicalCaseGateway(
            URI endpoint, String clientId, String bearerSecret,
            AuthorityProofSource proofSource, Sender sender) {
        if (!safeEndpoint(endpoint) || clientId == null
                || !clientId.matches("[A-Za-z0-9_.-]{1,96}") || bearerSecret == null
                || bearerSecret.length() < 32 || proofSource == null || sender == null) {
            throw new IllegalArgumentException("invalid inactive case transport configuration");
        }
        this.endpoint = endpoint;
        this.clientId = clientId;
        this.bearerSecret = bearerSecret;
        this.proofSource = proofSource;
        this.sender = sender;
    }

    @Override
    public AuthorityBackedCaseActions.TransportStatus send(AuthorityBackedCaseActions.Command command) {
        Objects.requireNonNull(command, "canonical staff command");
        try {
            ActorProof proof = proofSource.issue(command);
            if (proof == null) {
                return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
            }
            RawResponse reply = sender.send(request(command, proof));
            return status(reply, command);
        } catch (IOException exception) {
            // Connection may have applied command before acknowledgment. NEVER retry with a new id.
            return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
        }
    }

    private HttpRequest request(
            AuthorityBackedCaseActions.Command command, ActorProof proof) {
        String body;
        try {
            body = JSON.writeValueAsString(Map.of(
                    "case_id", command.caseId().toString(),
                    "origin_platform", command.origin().name().toLowerCase(java.util.Locale.ROOT),
                    "scope_id", command.scope(),
                    "expected_revision", command.expectedRevision(),
                    "staff_actor_id", command.actor(),
                    "command_id", command.commandId(),
                    "action", command.action().name(),
                    "actor_proof_issued_at", proof.issuedAt(),
                    "actor_proof_expires_at", proof.expiresAt(),
                    "actor_proof_signature", proof.signature()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("case command could not be encoded", exception);
        }
        return HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + bearerSecret)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static AuthorityBackedCaseActions.TransportStatus status(
            RawResponse response, AuthorityBackedCaseActions.Command command) throws IOException {
        try (InputStream body = response.body()) {
            if (response.status() == HTTP_CONFLICT) {
                return AuthorityBackedCaseActions.TransportStatus.STALE;
            }
            if (DENIED_STATUSES.contains(response.status())) {
                return AuthorityBackedCaseActions.TransportStatus.DENIED;
            }
            if (response.status() != HTTP_OK) {
                return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
            }
            // Read at most one byte beyond the cap; closing cancels the unread HTTP stream.
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
            }
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return confirmed(decoded, command)
                    ? AuthorityBackedCaseActions.TransportStatus.ACCEPTED
                    : AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
        }
    }

    private static boolean confirmed(String body, AuthorityBackedCaseActions.Command command) {
        try {
            JsonNode root = JSON.readTree(body);
            String state = command.action() == net.enthusia.staff.domain.modcase.CaseNotice.Action.CLAIM
                    ? "CLAIMED" : "CLOSED";
            return wellFormed(root)
                    && matchesIdentity(root, command)
                    && root.path("state").asText().equals(state);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private static boolean wellFormed(JsonNode root) {
        return root != null && root.isObject() && root.size() == 4
                && root.path("replay").isBoolean()
                && root.path("revision").isIntegralNumber();
    }

    private static boolean matchesIdentity(
            JsonNode root, AuthorityBackedCaseActions.Command command) {
        return root.path("case_id").asText().equals(command.caseId().toString())
                && root.path("revision").asLong() == command.expectedRevision() + 1;
    }

    private static boolean safeEndpoint(URI endpoint) {
        if (!safeParts(endpoint)) {
            return false;
        }
        return "https".equalsIgnoreCase(endpoint.getScheme()) || loopbackHttp(endpoint);
    }

    private static boolean safeParts(URI endpoint) {
        return endpoint != null && endpoint.getHost() != null
                && endpoint.getUserInfo() == null && endpoint.getQuery() == null
                && endpoint.getFragment() == null && PATH.equals(endpoint.getPath());
    }

    private static boolean loopbackHttp(URI endpoint) {
        if (!SCHEME_HTTP.equalsIgnoreCase(endpoint.getScheme())) {
            return false;
        }
        String host = endpoint.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "::1".equals(host);
    }

    private static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return request -> {
            // The four-second request timeout is not a total stalled-body read deadline
            // after ofInputStream returns headers. This opt-in gateway remains inactive.
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            return new RawResponse(response.statusCode(), response.body());
        };
    }
}
