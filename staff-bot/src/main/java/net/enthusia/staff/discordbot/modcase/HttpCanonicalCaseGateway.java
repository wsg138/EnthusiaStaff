package net.enthusia.staff.discordbot.modcase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;

/**
 * Single-attempt, opt-in StaffBot HTTP transport for the draft Moderation API case endpoint.
 * Not registered with JDA, Paper, staff UI or application bootstrap.
 */
public final class HttpCanonicalCaseGateway implements AuthorityBackedCaseActions.Gateway {
    private static final String PATH = "/v1/staff-case-commands";
    private static final int MAX_RESPONSE_BYTES = 4096;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI endpoint;
    private final String clientId;
    private final String bearerSecret;
    private final Sender sender;

    @FunctionalInterface
    interface Sender {
        RawResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    record RawResponse(int status, String body) {
        RawResponse {
            body = body == null ? "" : body;
        }
    }

    public HttpCanonicalCaseGateway(URI endpoint, String clientId, String bearerSecret) {
        this(endpoint, clientId, bearerSecret, defaultSender());
    }

    HttpCanonicalCaseGateway(URI endpoint, String clientId, String bearerSecret, Sender sender) {
        if (!safeEndpoint(endpoint) || clientId == null
                || !clientId.matches("[A-Za-z0-9_.-]{1,96}") || bearerSecret == null
                || bearerSecret.length() < 32 || sender == null) {
            throw new IllegalArgumentException("invalid inactive case transport configuration");
        }
        this.endpoint = endpoint;
        this.clientId = clientId;
        this.bearerSecret = bearerSecret;
        this.sender = sender;
    }

    @Override
    public AuthorityBackedCaseActions.TransportStatus send(AuthorityBackedCaseActions.Command command) {
        Objects.requireNonNull(command, "canonical staff command");
        HttpRequest request = request(command);
        try {
            RawResponse reply = sender.send(request);
            return status(reply, command);
        } catch (IOException exception) {
            // Connection may have applied command before acknowledgment. NEVER retry with a new id.
            return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
        }
    }

    private HttpRequest request(AuthorityBackedCaseActions.Command command) {
        String body;
        try {
            body = JSON.writeValueAsString(Map.of(
                    "case_id", command.caseId().toString(),
                    "origin_platform", command.origin().name().toLowerCase(java.util.Locale.ROOT),
                    "scope_id", command.scope(),
                    "expected_revision", command.expectedRevision(),
                    "staff_actor_id", command.actor(),
                    "command_id", command.commandId(),
                    "action", command.action().name()
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
            RawResponse response, AuthorityBackedCaseActions.Command command) {
        if (response.status() == 409) {
            return AuthorityBackedCaseActions.TransportStatus.STALE;
        }
        if (response.status() == 401 || response.status() == 403
                || response.status() == 404 || response.status() == 422) {
            return AuthorityBackedCaseActions.TransportStatus.DENIED;
        }
        if (response.status() != 200 || response.body().length() > MAX_RESPONSE_BYTES) {
            return AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
        }
        return confirmed(response.body(), command)
                ? AuthorityBackedCaseActions.TransportStatus.ACCEPTED
                : AuthorityBackedCaseActions.TransportStatus.RETRYABLE;
    }

    private static boolean confirmed(String body, AuthorityBackedCaseActions.Command command) {
        try {
            JsonNode root = JSON.readTree(body);
            String state = command.action() == net.enthusia.staff.domain.modcase.CaseNotice.Action.CLAIM
                    ? "CLAIMED" : "CLOSED";
            return root != null && root.isObject() && root.size() == 4
                    && root.path("case_id").asText().equals(command.caseId().toString())
                    && root.path("revision").isIntegralNumber()
                    && root.path("revision").asLong() == command.expectedRevision() + 1
                    && root.path("state").asText().equals(state)
                    && root.path("replay").isBoolean();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private static boolean safeEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !PATH.equals(endpoint.getPath())) {
            return false;
        }
        if ("https".equalsIgnoreCase(endpoint.getScheme())) {
            return true;
        }
        String host = endpoint.getHost();
        return "http".equalsIgnoreCase(endpoint.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host));
    }

    private static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return request -> {
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new RawResponse(response.statusCode(), response.body());
        };
    }
}
