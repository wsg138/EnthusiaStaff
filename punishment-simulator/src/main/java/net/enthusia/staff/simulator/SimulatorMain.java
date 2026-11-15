package net.enthusia.staff.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/** Standalone, loopback-only what-if browser tool. Never contacts Minecraft or MariaDB. */
public final class SimulatorMain {
    private static final int MAX_BODY = 65536;
    private final SimulatorModel model;
    private final ObjectMapper json = new ObjectMapper();

    private SimulatorMain(Path snapshot) {
        model = new SimulatorModel(snapshot);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: punishment-simulator <absolute-path-to-reason-policies.yml>");
            System.exit(2);
        }
        var app = new SimulatorMain(Path.of(args[0]));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 12);
        server.createContext("/", app::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(3));
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        System.out.println("LOCAL ONLY - Punishment Escalation Simulator");
        System.out.println("Open in browser: " + url);
        System.out.println("Source: " + app.model.catalog().get("source"));
        System.out.println("No database writes, punishments or Minecraft connections. Close this terminal to exit.");
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(url);
            } catch (IOException | SecurityException ignored) {
                // URL remains visible for manual opening.
            }
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                try (var resource = SimulatorMain.class.getResourceAsStream("/index.html")) {
                    if (resource == null) throw new IllegalStateException("Simulator UI missing");
                    send(exchange, 200, "text/html; charset=utf-8", resource.readAllBytes());
                }
            } else if ("/api/catalog".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, model.catalog());
            } else if ("/api/evaluate".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) throw new IllegalArgumentException("Scenario too large");
                sendJson(exchange, 200, model.evaluate(json.readTree(body)));
            } else {
                sendJson(exchange, 404, Map.of("error", "Not found"));
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            System.err.println("Simulator request failed: " + exception.getClass().getSimpleName());
            sendJson(exchange, 500, Map.of("error", "Simulator request failed"));
        } finally {
            exchange.close();
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.writeValueAsBytes(body));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] bytes)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
