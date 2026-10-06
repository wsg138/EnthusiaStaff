package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only, read-only moderation-state API for Enthusia AI. */
final class AiModerationReadApiServer implements AutoCloseable {
    private static final String BIND_HOST = "127.0.0.1";
    private static final int BIND_PORT = 8767;
    private static final int MAX_BODY_BYTES = 4_096;
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final String POST_METHOD = "POST";
    private static final String AUTHORIZATION = "Authorization";

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper json;
    private final AiModerationReadAuthenticator authenticator;
    private final ModerationReadApiRateLimiter rateLimiter;
    private final AiModerationReadApiService service;

    AiModerationReadApiServer(
            String token,
            AiModerationReadApiService service
    ) throws IOException {
        if (service == null) {
            throw new IllegalArgumentException("AI moderation read service must be present");
        }
        this.service = service;
        this.authenticator = new AiModerationReadAuthenticator(token);
        this.rateLimiter = new ModerationReadApiRateLimiter(
                REQUESTS_PER_MINUTE,
                Duration.ofMinutes(1)
        );
        this.json = ModerationReadApiServer.jsonMapper();
        this.server = HttpServer.create(bindAddress(), 0);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "enthusia-ai-moderation-read");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/v1/ai/moderation-state", this::handle);
    }

    static InetSocketAddress bindAddress() {
        return new InetSocketAddress(BIND_HOST, BIND_PORT);
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
                respond(exchange, 405,
                        new AiModerationReadApiModel.ErrorResponse("method_not_allowed", "Method not allowed."));
                return;
            }
            if (!authenticator.accepts(exchange.getRequestHeaders().getFirst(AUTHORIZATION))) {
                respond(exchange, 401,
                        new AiModerationReadApiModel.ErrorResponse("unauthorized", "Request rejected."));
                return;
            }
            if (!rateLimiter.tryAcquire()) {
                respond(exchange, 429,
                        new AiModerationReadApiModel.ErrorResponse("rate_limited", "Too many read requests."));
                return;
            }

            byte[] body = readBody(exchange);
            if (body == null) {
                respond(exchange, 413,
                        new AiModerationReadApiModel.ErrorResponse("request_too_large", "Request rejected."));
                return;
            }
            execute(exchange, body);
        }
    }

    private void execute(HttpExchange exchange, byte[] body) throws IOException {
        try {
            AiModerationReadApiModel.Request request = parseRequest(json, body);
            respond(exchange, 200, service.read(request));
        } catch (AiModerationReadApiService.MissingTargetException exception) {
            respond(exchange, 404,
                    new AiModerationReadApiModel.ErrorResponse("target_not_found", "Target was not found."));
        } catch (AiModerationReadApiService.AmbiguousTargetException exception) {
            respond(exchange, 409,
                    new AiModerationReadApiModel.ErrorResponse("target_ambiguous", "Target is ambiguous."));
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400,
                    new AiModerationReadApiModel.ErrorResponse("invalid_request", "Request rejected."));
        } catch (RuntimeException exception) {
            respond(exchange, 503,
                    new AiModerationReadApiModel.ErrorResponse(
                            "source_unavailable",
                            "Moderation state is temporarily unavailable."
                    ));
        }
    }

    static AiModerationReadApiModel.Request parseRequest(
            ObjectMapper json,
            byte[] body
    ) {
        try {
            AiModerationReadApiModel.Request request =
                    json.readValue(body, AiModerationReadApiModel.Request.class);
            if (request == null) {
                throw new IllegalArgumentException("request JSON must contain an object");
            }
            return request;
        } catch (IOException exception) {
            throw new IllegalArgumentException("request JSON is invalid", exception);
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
        exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
