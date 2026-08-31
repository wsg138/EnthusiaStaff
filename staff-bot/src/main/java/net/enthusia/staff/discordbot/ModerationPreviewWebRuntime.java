package net.enthusia.staff.discordbot;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hosts the staging-only moderation web preview. It has no moderation or persistence adapter. */
final class ModerationPreviewWebRuntime implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ModerationPreviewWebRuntime.class.getName());
    private static final Duration LAUNCH_TTL = Duration.ofMinutes(2);
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final int MAX_REQUEST_BYTES = 65_536;
    private static final int WORKER_THREADS = 4;
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String METHOD_NOT_ALLOWED = "Method not allowed.";
    private static final String SESSION_COOKIE = "enthusia_mod_preview";
    private static final String SAMPLE_TARGET = "sample-river-ash";
    private static final String HTML = "text/html; charset=utf-8";
    private static final String CSS = "text/css; charset=utf-8";
    private static final String JAVASCRIPT = "text/javascript; charset=utf-8";
    private static final Map<String, StaticResource> STATIC_RESOURCES = Map.of(
            "/", new StaticResource("/moderation-preview/index.html", HTML),
            "/moderation", new StaticResource("/moderation-preview/index.html", HTML),
            "/assets/app.css", new StaticResource("/moderation-preview/app.css", CSS),
            "/assets/model.js", new StaticResource("/moderation-preview/model.js", JAVASCRIPT),
            "/assets/app.js", new StaticResource("/moderation-preview/app.js", JAVASCRIPT),
            "/assets/workflow.js", new StaticResource("/moderation-preview/workflow.js", JAVASCRIPT),
            "/assets/review.js", new StaticResource("/moderation-preview/review.js", JAVASCRIPT));

    private final ModerationPreviewWebConfig config;
    private final ModerationPreviewLaunchTicketService tickets;
    private final ModerationPreviewWebSessionStore sessions;
    private final Object lifecycleLock = new Object();
    private boolean started;
    private boolean closed;
    private HttpServer server;
    private ExecutorService executor;

    ModerationPreviewWebRuntime(
            ModerationPreviewWebConfig config,
            ModerationPreviewLaunchTicketService tickets,
            ModerationPreviewWebSessionStore sessions
    ) {
        this.config = config;
        this.tickets = tickets;
        this.sessions = sessions;
    }

    static ModerationPreviewWebRuntime fromEnvironment(int capacity) {
        return fromConfig(ModerationPreviewWebConfig.fromEnvironment(System.getenv()), capacity);
    }

    static ModerationPreviewWebRuntime fromConfig(ModerationPreviewWebConfig config, int capacity) {
        return new ModerationPreviewWebRuntime(
                config,
                new ModerationPreviewLaunchTicketService(capacity, LAUNCH_TTL),
                new ModerationPreviewWebSessionStore(capacity, SESSION_TTL));
    }

    void start() {
        synchronized (lifecycleLock) {
            ensureOpen();
            if (started) {
                return;
            }
            startLocked();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("preview web runtime is closed");
        }
    }

    private void startLocked() {
        HttpServer createdServer = null;
        ExecutorService createdExecutor = null;
        try {
            createdServer = HttpServer.create(config.bindAddress(), 0);
            createdExecutor = Executors.newFixedThreadPool(
                    WORKER_THREADS,
                    Thread.ofPlatform().name("moderation-preview-web-", 0).factory());
            createdServer.setExecutor(createdExecutor);
            createdServer.createContext("/", this::handle);
            createdServer.start();
            server = createdServer;
            executor = createdExecutor;
            started = true;
            if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
                LOGGER.log(System.Logger.Level.INFO, "moderation_preview_web_started bind={0}", server.getAddress());
            }
        } catch (IOException | RuntimeException exception) {
            stopResources(createdServer, createdExecutor);
            closed = true;
            throw new IllegalStateException("staging moderation web preview failed to bind", exception);
        }
    }

    InetSocketAddress boundAddress() {
        synchronized (lifecycleLock) {
            if (!started || closed || server == null) {
                throw new IllegalStateException("preview web runtime is not started");
            }
            return server.getAddress();
        }
    }

    Optional<URI> issueLaunchUri(long actorId, long guildId) {
        return config.publicBaseUri().map(base -> {
            String ticket = tickets.issue(actorId, guildId, SAMPLE_TARGET);
            String encoded = URLEncoder.encode(ticket, StandardCharsets.UTF_8);
            return URI.create(base.toString() + "/launch?t=" + encoded);
        });
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            applySecurityHeaders(exchange.getResponseHeaders());
            route(exchange);
        } catch (RuntimeException exception) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING, "moderation_preview_web_request_failed type={0}",
                        exception.getClass().getSimpleName());
            }
            if (exchange.getResponseCode() == -1) {
                respondText(exchange, 500, "Preview request failed.");
            }
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/launch".equals(path)) {
            handleLaunch(exchange);
            return;
        }
        if ("/api/session".equals(path)) {
            handleSession(exchange);
            return;
        }
        if ("/api/simulate".equals(path)) {
            handleSimulation(exchange);
            return;
        }
        StaticResource resource = STATIC_RESOURCES.get(path);
        if (resource != null) {
            serveProtectedResource(exchange, resource);
            return;
        }
        respondText(exchange, 404, "Not found.");
    }

    private void handleLaunch(HttpExchange exchange) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            respondText(exchange, 405, METHOD_NOT_ALLOWED);
            return;
        }
        String token = queryParameter(exchange.getRequestURI(), "t").orElse("");
        ModerationPreviewLaunchTicketService.ConsumeResult result = tickets.consume(token);
        if (result.status() != ModerationPreviewLaunchTicketService.Status.ACCEPTED) {
            respondText(exchange, 401, "This moderation link is invalid, expired, or already used.");
            return;
        }
        ModerationPreviewWebSessionStore.Session session = sessions.create(result.claims().orElseThrow());
        exchange.getResponseHeaders().add("Set-Cookie", sessionCookie(session.id()));
        exchange.getResponseHeaders().set("Location", "/moderation");
        exchange.sendResponseHeaders(303, -1);
    }

    private void handleSession(HttpExchange exchange) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            respondText(exchange, 405, METHOD_NOT_ALLOWED);
            return;
        }
        Optional<ModerationPreviewWebSessionStore.Session> session = session(exchange);
        if (session.isEmpty()) {
            respondText(exchange, 401, "Session expired.");
            return;
        }
        ModerationPreviewWebSessionStore.Session value = session.get();
        String responseJson = "{\"actorId\":\"" + value.claims().actorId()
                + "\",\"guildId\":\"" + value.claims().guildId()
                + "\",\"targetKey\":\"" + json(value.claims().targetKey())
                + "\",\"csrfToken\":\"" + json(value.csrfToken())
                + "\",\"expiresAt\":\"" + value.expiresAt() + "\",\"staging\":true}";
        respond(exchange, 200, "application/json; charset=utf-8", responseJson.getBytes(StandardCharsets.UTF_8));
    }

    private void handleSimulation(HttpExchange exchange) throws IOException {
        if (!POST.equals(exchange.getRequestMethod())) {
            respondText(exchange, 405, METHOD_NOT_ALLOWED);
            return;
        }
        Optional<ModerationPreviewWebSessionStore.Session> session = session(exchange);
        if (session.isEmpty() || !csrfMatches(exchange, session.get().csrfToken())) {
            respondText(exchange, 403, "Session verification failed.");
            return;
        }
        if (!consumeBoundedBody(exchange)) {
            respondText(exchange, 413, "Preview request is too large.");
            return;
        }
        String responseJson = "{\"status\":\"complete\",\"message\":\"Simulation complete\","
                + "\"detail\":\"No live moderation action was performed.\"}";
        respond(exchange, 200, "application/json; charset=utf-8", responseJson.getBytes(StandardCharsets.UTF_8));
    }

    private void serveProtectedResource(HttpExchange exchange, StaticResource resource) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            respondText(exchange, 405, METHOD_NOT_ALLOWED);
            return;
        }
        if (session(exchange).isEmpty()) {
            respondText(exchange, 401, "Open this panel from Discord.");
            return;
        }
        try (InputStream input = ModerationPreviewWebRuntime.class.getResourceAsStream(resource.path())) {
            if (input == null) {
                respondText(exchange, 500, "Preview resource unavailable.");
                return;
            }
            respond(exchange, 200, resource.contentType(), input.readAllBytes());
        }
    }

    private Optional<ModerationPreviewWebSessionStore.Session> session(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) {
            return Optional.empty();
        }
        for (String value : cookie.split(";")) {
            String[] pair = value.trim().split("=", 2);
            if (pair.length == 2 && SESSION_COOKIE.equals(pair[0])) {
                return sessions.find(pair[1]);
            }
        }
        return Optional.empty();
    }

    private static boolean csrfMatches(HttpExchange exchange, String expected) {
        String supplied = exchange.getRequestHeaders().getFirst("X-Preview-Csrf");
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean consumeBoundedBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        return body.length <= MAX_REQUEST_BYTES;
    }

    private String sessionCookie(String value) {
        String secure = config.secureCookie() ? "; Secure" : "";
        return SESSION_COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + SESSION_TTL.toSeconds() + secure;
    }

    private static Optional<String> queryParameter(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        try {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return Optional.of(parts.length == 2
                            ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                            : "");
                }
            }
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void applySecurityHeaders(Headers headers) {
        headers.set("Cache-Control", "no-store");
        headers.set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                        + "connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    private static void respondText(HttpExchange exchange, int status, String text) throws IOException {
        respond(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            stopResources(server, executor);
        }
    }

    private static void stopResources(HttpServer currentServer, ExecutorService currentExecutor) {
        if (currentServer != null) {
            currentServer.stop(0);
        }
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
    }

    private record StaticResource(String path, String contentType) {
    }
}
