package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.protocol.CommandBridgeHttpSigning;
import net.enthusia.staff.protocol.CommandBridgeWireCodec;

/** Single-attempt StaffBot-to-Paper command transport. No failure path loops or retries a request. */
public final class HttpMinecraftCommandBridgeClient {
    public static final String ENDPOINT_PATH = "/v1/discord-command";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, URI> endpoints;
    private final String credential;
    private final Duration timeout;
    private final Clock clock;
    private final Supplier<String> nonceSource;
    private final RawHttpSender sender;
    private final CommandBridgeWireCodec codec = new CommandBridgeWireCodec();

    public HttpMinecraftCommandBridgeClient(
            Map<String, URI> endpoints,
            String credential,
            Duration timeout
    ) {
        this(endpoints, credential, timeout, Clock.systemUTC(), HttpMinecraftCommandBridgeClient::nonce,
                defaultSender(timeout));
    }

    HttpMinecraftCommandBridgeClient(
            Map<String, URI> endpoints,
            String credential,
            Duration timeout,
            Clock clock,
            Supplier<String> nonceSource,
            RawHttpSender sender
    ) {
        this.endpoints = validateEndpoints(endpoints);
        if (credential == null || credential.isBlank() || timeout == null || timeout.isNegative() || timeout.isZero()
                || clock == null || nonceSource == null || sender == null) {
            throw new IllegalArgumentException("command bridge HTTP client configuration is invalid");
        }
        this.credential = credential;
        this.timeout = timeout;
        this.clock = clock;
        this.nonceSource = nonceSource;
        this.sender = sender;
    }

    public Result dispatch(CommandBridgeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("command bridge request is required");
        }
        URI endpoint = endpoints.get(request.targetServer());
        if (endpoint == null) {
            return Result.failed(Status.INVALID_SERVER);
        }
        String body = codec.encodeRequest(request);
        String nonce = nonceSource.get();
        CommandBridgeHttpSigning.RequestProof proof = CommandBridgeHttpSigning.signRequest(
                credential,
                "POST",
                endpoint.getRawPath(),
                body,
                clock.instant(),
                nonce
        );
        HttpRequest httpRequest = request(endpoint, body, proof);
        return sendOnce(httpRequest, proof.nonce());
    }

    private Result sendOnce(HttpRequest request, String nonce) {
        RawResponse response;
        try {
            response = sender.send(request);
        } catch (ConnectException failure) {
            return Result.failed(Status.ENDPOINT_UNAVAILABLE);
        } catch (IOException failure) {
            return Result.failed(Status.AMBIGUOUS_FAILURE);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return Result.failed(Status.AMBIGUOUS_FAILURE);
        }
        if (!CommandBridgeHttpSigning.verifyResponse(
                credential, nonce, response.status(), response.body(), response.signature())) {
            return Result.failed(Status.INVALID_RESPONSE);
        }
        Optional<CommandBridgeResponse> decoded = codec.decodeResponse(response.body());
        return decoded.map(Result::received).orElseGet(() -> Result.failed(Status.INVALID_RESPONSE));
    }

    private HttpRequest request(
            URI endpoint,
            String body,
            CommandBridgeHttpSigning.RequestProof proof
    ) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header(CommandBridgeHttpSigning.TIMESTAMP_HEADER, proof.timestamp())
                .header(CommandBridgeHttpSigning.NONCE_HEADER, proof.nonce())
                .header(CommandBridgeHttpSigning.SIGNATURE_HEADER, proof.signature())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static RawHttpSender defaultSender(Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return request -> {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String signature = response.headers()
                    .firstValue(CommandBridgeHttpSigning.RESPONSE_SIGNATURE_HEADER)
                    .orElse("");
            return new RawResponse(response.statusCode(), response.body(), signature);
        };
    }

    private static Map<String, URI> validateEndpoints(Map<String, URI> configured) {
        if (configured == null) {
            throw new IllegalArgumentException("command bridge endpoint allowlist is required");
        }
        Map<String, URI> validated = new HashMap<>();
        configured.forEach((server, uri) -> {
            if (!validServer(server) || !validEndpoint(uri) || validated.putIfAbsent(server, uri) != null) {
                throw new IllegalArgumentException("command bridge endpoint allowlist is invalid");
            }
        });
        return Map.copyOf(validated);
    }

    private static boolean validServer(String server) {
        return server != null && server.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private static boolean validEndpoint(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            return false;
        }
        boolean scheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        return scheme && ENDPOINT_PATH.equals(uri.getPath());
    }

    private static String nonce() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @FunctionalInterface
    interface RawHttpSender {
        RawResponse send(HttpRequest request) throws IOException, InterruptedException;
    }

    record RawResponse(int status, String body, String signature) {
        RawResponse {
            body = body == null ? "" : body;
            signature = signature == null ? "" : signature;
        }
    }

    public record Result(Status status, Optional<CommandBridgeResponse> response) {
        public Result {
            if (status == null || response == null || (status == Status.RECEIVED) != response.isPresent()) {
                throw new IllegalArgumentException("command bridge transport result is invalid");
            }
        }

        static Result received(CommandBridgeResponse response) {
            return new Result(Status.RECEIVED, Optional.of(response));
        }

        static Result failed(Status status) {
            return new Result(status, Optional.empty());
        }
    }

    public enum Status {
        RECEIVED,
        INVALID_SERVER,
        ENDPOINT_UNAVAILABLE,
        AMBIGUOUS_FAILURE,
        INVALID_RESPONSE
    }
}
