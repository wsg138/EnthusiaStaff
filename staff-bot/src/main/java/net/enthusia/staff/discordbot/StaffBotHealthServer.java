package net.enthusia.staff.discordbot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only liveness/readiness surface with deliberately non-sensitive payloads. */
final class StaffBotHealthServer implements HealthEndpoint {
    private final StaffBotHealth health;
    private final HttpServer server;
    private final ExecutorService executor;

    StaffBotHealthServer(InetSocketAddress address, StaffBotHealth health) throws IOException {
        this.health = Objects.requireNonNull(health, "health");
        Objects.requireNonNull(address, "address");
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon(true).name("staff-bot-health-", 0).factory());
        server.setExecutor(executor);
        server.createContext("/health", exchange -> respond(exchange, false));
        server.createContext("/ready", exchange -> respond(exchange, true));
    }

    @Override
    public void start() {
        server.start();
    }

    InetSocketAddress boundAddress() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void respond(HttpExchange exchange, boolean readiness) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            StaffBotHealth.Snapshot snapshot = health.snapshot();
            boolean acceptable = readiness ? health.isReady() : health.isLive();
            int status = acceptable ? 200 : 503;
            String body = "{\"environment\":\"" + json(health.environment().label())
                    + "\",\"status\":\"" + snapshot.phase().name()
                    + "\",\"ready\":" + health.isReady()
                    + ",\"reason\":\"" + json(snapshot.reason())
                    + "\",\"rejectedWork\":" + snapshot.rejectedWork()
                    + "}\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
