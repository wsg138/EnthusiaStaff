package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only authenticated private read API. It exposes no mutation route. */
final class ModerationReadApiServer implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ModerationReadApiServer.class.getName());
    private static final String BIND_HOST = "127.0.0.1";
    private static final String POST_METHOD = "POST";
    private static final String OPTIONS_METHOD = "OPTIONS";
    static final String PREVIEW_ORIGIN = "https://staff-staging.enthusia.info";
    private static final String ORIGIN_HEADER = "Origin";
    private static final String REQUEST_METHOD_HEADER = "Access-Control-Request-Method";
    private static final String REQUEST_HEADERS_HEADER = "Access-Control-Request-Headers";
    private static final String ALLOW_HEADERS = String.join(", ",
            "Content-Type",
            ModerationReadApiAuthenticator.TIMESTAMP_HEADER,
            ModerationReadApiAuthenticator.NONCE_HEADER,
            ModerationReadApiAuthenticator.SIGNATURE_HEADER);
    private static final Set<String> PREFLIGHT_HEADERS = Set.of(
            "content-type",
            ModerationReadApiAuthenticator.TIMESTAMP_HEADER.toLowerCase(Locale.ROOT),
            ModerationReadApiAuthenticator.NONCE_HEADER.toLowerCase(Locale.ROOT),
            ModerationReadApiAuthenticator.SIGNATURE_HEADER.toLowerCase(Locale.ROOT));
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
            if (OPTIONS_METHOD.equals(exchange.getRequestMethod())) {
                handlePreflight(exchange);
                return;
            }
            if (!originAllowed(exchange.getRequestHeaders().get(ORIGIN_HEADER))) {
                respond(exchange, 403, new ModerationReadApiModel.ErrorResponse("forbidden", "Access denied."));
                return;
            }
            if (!POST_METHOD.equals(exchange.getRequestMethod())) {
                respond(exchange, 405, new ModerationReadApiModel.ErrorResponse("method_not_allowed", "Method not allowed."));
                return;
            }
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
            execute(exchange, body, bootstrap);
        }
    }

    private void handlePreflight(HttpExchange exchange) throws IOException {
        if (!browserOrigin(exchange.getRequestHeaders().get(ORIGIN_HEADER))
                || !POST_METHOD.equals(exchange.getRequestHeaders().getFirst(REQUEST_METHOD_HEADER))
                || !validPreflightHeaders(exchange.getRequestHeaders().getFirst(REQUEST_HEADERS_HEADER))) {
            respond(exchange, 403, new ModerationReadApiModel.ErrorResponse("forbidden", "Access denied."));
            return;
        }
        applyCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", POST_METHOD);
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", ALLOW_HEADERS);
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(204, -1);
    }

    private void execute(HttpExchange exchange, byte[] body, boolean bootstrap) throws IOException {
        try {
            ModerationReadApiModel.ReadRequest request = parseRequest(json, body);
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

    static ModerationReadApiModel.ReadRequest parseRequest(ObjectMapper json, byte[] body) throws IOException {
        try {
            ModerationReadApiModel.ReadRequest request = json.readValue(body, ModerationReadApiModel.ReadRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("request JSON must contain an object");
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("request JSON is invalid", exception);
        }
    }

    static boolean originAllowed(List<String> origins) {
        return origins == null || origins.isEmpty() || browserOrigin(origins);
    }

    static boolean browserOrigin(List<String> origins) {
        return origins != null && origins.size() == 1 && PREVIEW_ORIGIN.equals(origins.getFirst());
    }

    static boolean validPreflightHeaders(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        Set<String> requested = new HashSet<>();
        for (String part : raw.split(",")) {
            String header = part.trim().toLowerCase(Locale.ROOT);
            if (header.isEmpty() || !PREFLIGHT_HEADERS.contains(header)) {
                return false;
            }
            requested.add(header);
        }
        return requested.equals(PREFLIGHT_HEADERS);
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
        applyCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void applyCorsHeaders(HttpExchange exchange) {
        if (!browserOrigin(exchange.getRequestHeaders().get(ORIGIN_HEADER))) {
            return;
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", PREVIEW_ORIGIN);
        exchange.getResponseHeaders().add("Vary", ORIGIN_HEADER);
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
