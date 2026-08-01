package net.enthusia.staff.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class WebsiteApiServer implements AutoCloseable {
    private final WebsiteApiServerConfiguration configuration;
    private final WebsiteApiAuthenticator authenticator;
    private final WebsiteApiRouter router;
    private final Clock clock;
    private final ErrorReporter errors;
    private final ObjectMapper json = new ObjectMapper();
    private final Object lifecycleLock = new Object();
    private WebsiteApiRuntime runtime;

    WebsiteApiServer(
            WebsiteApiServerConfiguration configuration,
            WebsiteApiAuthenticator authenticator,
            WebsiteApiRouter router,
            Clock clock,
            ErrorReporter errors
    ) {
        if (configuration == null || authenticator == null || router == null
                || clock == null || errors == null) {
            throw new IllegalArgumentException("Website API server dependencies are required");
        }
        this.configuration = configuration;
        this.authenticator = authenticator;
        this.router = router;
        this.clock = clock;
        this.errors = errors;
    }

    void start() throws IOException {
        synchronized (lifecycleLock) {
            if (runtime != null) {
                throw new IllegalStateException("Website API server is already running");
            }
            runtime = WebsiteApiRuntime.start(configuration, this::handle);
        }
    }

    private void handle(HttpExchange exchange) {
        String requestId = UUID.randomUUID().toString();
        try {
            requireLoopback(exchange);
            byte[] body = readBody(exchange);
            String target = rawTarget(exchange);
            authenticator.authenticate(
                    exchange.getRequestMethod(),
                    target,
                    exchange.getRequestHeaders(),
                    body,
                    clock.instant()
            );
            Object response = router.route(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI(),
                    exchange.getRequestHeaders(),
                    body
            );
            send(exchange, 200, response, requestId);
        } catch (WebsiteApiException exception) {
            sendError(exchange, exception.status(), exception.code(), exception.getMessage(), requestId);
        } catch (WebsiteModerationException exception) {
            sendError(
                    exchange,
                    moderationStatus(exception),
                    exception.code(),
                    exception.getMessage(),
                    requestId
            );
        } catch (RejectedExecutionException exception) {
            sendOverloaded(exchange, requestId);
        } catch (RuntimeException exception) {
            errors.report("Website API request " + requestId + " failed", exception);
            sendUnavailable(exchange, requestId);
        } finally {
            exchange.close();
        }
    }

    private void sendOverloaded(HttpExchange exchange, String requestId) {
        sendError(
                exchange,
                503,
                "API_OVERLOADED",
                "The moderation API is temporarily busy",
                requestId
        );
    }

    private void sendUnavailable(HttpExchange exchange, String requestId) {
        sendError(
                exchange,
                503,
                "MODERATION_API_UNAVAILABLE",
                "The moderation service could not complete the request",
                requestId
        );
    }

    private byte[] readBody(HttpExchange exchange) {
        String declared = exchange.getRequestHeaders().getFirst("content-length");
        if (declared != null) {
            validateDeclaredLength(declared);
        }
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(configuration.maximumBodyBytes() + 1);
            if (body.length > configuration.maximumBodyBytes()) {
                throw requestTooLarge();
            }
            return body;
        } catch (IOException exception) {
            throw badRequest("REQUEST_READ_FAILED", "The request body could not be read");
        }
    }

    private void validateDeclaredLength(String declared) {
        try {
            long length = Long.parseLong(declared);
            if (length < 0 || length > configuration.maximumBodyBytes()) {
                throw requestTooLarge();
            }
        } catch (NumberFormatException exception) {
            throw badRequest("INVALID_CONTENT_LENGTH", "The request content length is invalid");
        }
    }

    private void sendError(
            HttpExchange exchange,
            int status,
            String code,
            String message,
            String requestId
    ) {
        send(
                exchange,
                status,
                Map.of(
                        "error", Map.of("code", code, "message", message),
                        "requestId", requestId
                ),
                requestId
        );
    }

    private void send(HttpExchange exchange, int status, Object payload, String requestId) {
        byte[] encoded;
        int responseStatus = status;
        try {
            encoded = json.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            errors.report("Website API response " + requestId + " could not be encoded", exception);
            encoded = "{\"error\":{\"code\":\"RESPONSE_ENCODING_FAILED\",\"message\":\"The response could not be encoded\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            responseStatus = 500;
        }
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.set("content-type", "application/json; charset=utf-8");
            headers.set("cache-control", "no-store");
            headers.set("x-content-type-options", "nosniff");
            headers.set("referrer-policy", "no-referrer");
            headers.set("x-request-id", requestId);
            exchange.sendResponseHeaders(responseStatus, encoded.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(encoded);
            }
        } catch (IOException exception) {
            errors.report("Website API response " + requestId + " could not be sent", exception);
        }
    }

    private static void requireLoopback(HttpExchange exchange) {
        if (exchange.getRemoteAddress() == null
                || exchange.getRemoteAddress().getAddress() == null
                || !exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
            throw new WebsiteApiException(403, "LOOPBACK_REQUIRED", "The API is loopback-only");
        }
    }

    private static int moderationStatus(WebsiteModerationException exception) {
        return switch (exception.kind()) {
            case INVALID -> 400;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case INELIGIBLE -> 422;
            case UNAVAILABLE -> 503;
        };
    }

    private static String rawTarget(HttpExchange exchange) {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        return query == null ? path : path + '?' + query;
    }

    private static WebsiteApiException requestTooLarge() {
        return new WebsiteApiException(
                413,
                "REQUEST_TOO_LARGE",
                "The request body exceeds the accepted limit"
        );
    }

    private static WebsiteApiException badRequest(String code, String message) {
        return new WebsiteApiException(400, code, message);
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment") // Clearing the field permits a clean restart after shutdown.
    public void close() {
        synchronized (lifecycleLock) {
            WebsiteApiRuntime current = runtime;
            runtime = null;
            if (current != null) {
                current.close();
            }
        }
    }

    @FunctionalInterface
    interface ErrorReporter {
        void report(String message, Throwable failure);
    }
}
