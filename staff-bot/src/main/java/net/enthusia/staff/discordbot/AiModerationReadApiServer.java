package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Private bounded read-only moderation-state endpoint for Enthusia AI. */
final class AiModerationReadApiServer implements AutoCloseable {
    static final String SUBJECT_STATE_PATH = "/v1/ai/moderation/subject-state";
    private static final String POST_METHOD = "POST";
    private static final int MAX_BODY_BYTES = 4_096;
    private static final int MAX_IN_FLIGHT = 16;
    private static final int WORKER_THREADS = 2;

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper json;
    @FunctionalInterface
    interface SubjectStateReader {
        AiModerationReadApiModel.SubjectStateResponse read(
                AiModerationReadApiModel.SubjectStateRequest request);
    }

    private final SubjectStateReader service;
    private final byte[] expectedBearerDigest;
    private final AtomicInteger inFlight = new AtomicInteger();

    AiModerationReadApiServer(
            AiModerationReadConfiguration configuration,
            StaffModerationReadService reads
    ) throws IOException {
        this(
                configuration.host(),
                configuration.port(),
                configuration.bearerToken(),
                new AiModerationReadService(reads, Clock.systemUTC())::subjectState
        );
    }

    AiModerationReadApiServer(
            String host,
            int port,
            String bearerToken,
            SubjectStateReader service
    ) throws IOException {
        this.service = Objects.requireNonNull(service, "service");
        this.expectedBearerDigest = digest("Bearer " + Objects.requireNonNull(bearerToken, "bearerToken"));
        this.json = ModerationReadApiServer.jsonMapper();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.executor = Executors.newFixedThreadPool(WORKER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "enthusia-ai-moderation-read-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext(SUBJECT_STATE_PATH, this::handle);
    }

    void start() {
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!POST_METHOD.equals(exchange.getRequestMethod())) {
                respond(exchange, 405, error("method_not_allowed", "Method not allowed."));
                return;
            }
            if (!authenticated(exchange)) {
                respond(exchange, 401, error("unauthorized", "Request rejected."));
                return;
            }
            if (inFlight.incrementAndGet() > MAX_IN_FLIGHT) {
                inFlight.decrementAndGet();
                respond(exchange, 429, error("rate_limited", "Too many read requests."));
                return;
            }
            try {
                byte[] body = readBody(exchange);
                if (body == null) {
                    respond(exchange, 413, error("request_too_large", "Request rejected."));
                    return;
                }
                AiModerationReadApiModel.SubjectStateRequest request = parse(body);
                respond(exchange, 200, service.read(request));
            } catch (IllegalArgumentException exception) {
                respond(exchange, 400, error("invalid_request", "Request rejected."));
            } catch (RuntimeException exception) {
                respond(exchange, 503, error("source_unavailable", "Moderation data is temporarily unavailable."));
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    private boolean authenticated(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || authorization.length() > 600) {
            return false;
        }
        return MessageDigest.isEqual(expectedBearerDigest, digest(authorization));
    }

    private AiModerationReadApiModel.SubjectStateRequest parse(byte[] body) {
        try {
            AiModerationReadApiModel.SubjectStateRequest request =
                    json.readValue(body, AiModerationReadApiModel.SubjectStateRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("request must be present");
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("request JSON is invalid", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("request body could not be read", exception);
        }
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(MAX_BODY_BYTES + 1);
            return body.length > MAX_BODY_BYTES ? null : body;
        }
    }

    private void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = json.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (status == 429) {
            exchange.getResponseHeaders().set("Retry-After", "1");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static AiModerationReadApiModel.ErrorResponse error(String code, String message) {
        return new AiModerationReadApiModel.ErrorResponse(code, message);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
