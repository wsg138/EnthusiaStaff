package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only authenticated private read API. It exposes no mutation route. */
final class ModerationReadApiServer implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ModerationReadApiServer.class.getName());
    private static final String BIND_HOST = "127.0.0.1";
    private static final String POST_METHOD = "POST";
    private static final int BIND_PORT = 8766;
    private static final int MAX_BODY_BYTES = 65_536;
    private static final int WORKER_THREADS = 2;
    private static final int REQUESTS_PER_MINUTE = 120;

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper json;
    private final ModerationReadApiAuthenticator authenticator;
    private final ModerationReadApiRateLimiter rateLimiter;
    private final ModerationReadApiService service;

    ModerationReadApiServer(String discordBotToken, ModerationReadApiService service) throws IOException {
        this.service = Objects.requireNonNull(service, "service");
        this.authenticator = new ModerationReadApiAuthenticator(discordBotToken);
        this.rateLimiter = new ModerationReadApiRateLimiter(REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
        this.json = new ObjectMapper().registerModule(new Jdk8Module());
        this.server = HttpServer.create(bindAddress(), 0);
        this.executor = Executors.newFixedThreadPool(WORKER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "enthusia-moderation-read-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/v1/moderation/bootstrap", exchange -> handle(exchange, true));
        server.createContext("/v1/moderation/messages", exchange -> handle(exchange, false));
    }

    static InetSocketAddress bindAddress() {
        return new InetSocketAddress(BIND_HOST, BIND_PORT);
    }

    void start() {
        server.start();
        log("moderation_read_api_started", null);
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange, boolean bootstrap) throws IOException {
        try (exchange) {
            byte[] body = readBody(exchange);
            if (body == null) {
                respond(exchange, 413, new ModerationReadApiModel.ErrorResponse("request_too_large", "Request rejected."));
                return;
            }
            ModerationReadApiAuthenticator.Result authentication = authenticate(exchange, body);
            if (authentication != ModerationReadApiAuthenticator.Result.ACCEPTED) {
                respond(exchange, 401, new ModerationReadApiModel.ErrorResponse("unauthorized", "Request rejected."));
                return;
            }
            if (!rateLimiter.tryAcquire()) {
                respond(exchange, 429, new ModerationReadApiModel.ErrorResponse("rate_limited", "Too many read requests."));
                return;
            }
            if (!POST_METHOD.equals(exchange.getRequestMethod())) {
                respond(exchange, 405, new ModerationReadApiModel.ErrorResponse("method_not_allowed", "Method not allowed."));
                return;
            }
            execute(exchange, body, bootstrap);
        }
    }

    private void execute(HttpExchange exchange, byte[] body, boolean bootstrap) throws IOException {
        try {
            ModerationReadApiModel.ReadRequest request = json.readValue(body, ModerationReadApiModel.ReadRequest.class);
            Object response = bootstrap ? service.bootstrap(request) : service.messages(request);
            respond(exchange, 200, response);
        } catch (StaffReadAuthorization.DeniedException | LinkedStaffActorResolver.MissingStaffLinkException exception) {
            respond(exchange, 403, new ModerationReadApiModel.ErrorResponse("forbidden", "Access denied."));
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, new ModerationReadApiModel.ErrorResponse("invalid_request", "Request rejected."));
        } catch (RuntimeException exception) {
            log("moderation_read_api_failure", exception);
            respond(exchange, 503, new ModerationReadApiModel.ErrorResponse(
                    "source_unavailable", "Moderation data is temporarily unavailable."));
        }
    }

    private ModerationReadApiAuthenticator.Result authenticate(HttpExchange exchange, byte[] body) {
        String path = exchange.getRequestURI().getPath();
        return authenticator.verify(
                exchange.getRequestMethod(), path, body,
                exchange.getRequestHeaders().getFirst(ModerationReadApiAuthenticator.TIMESTAMP_HEADER),
                exchange.getRequestHeaders().getFirst(ModerationReadApiAuthenticator.NONCE_HEADER),
                exchange.getRequestHeaders().getFirst(ModerationReadApiAuthenticator.SIGNATURE_HEADER)
        );
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

    private static void log(String code, Throwable failure) {
        if (!LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            return;
        }
        String type = failure == null ? "none" : failure.getClass().getSimpleName();
        LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, type);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
