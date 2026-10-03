package net.enthusia.staff.paper.commandbridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.domain.commandbridge.CommandBridgeService;
import net.enthusia.staff.protocol.CommandBridgeHttpSigning;
import net.enthusia.staff.protocol.CommandBridgeRequestAuthenticator;
import net.enthusia.staff.protocol.CommandBridgeWireCodec;

/** Authenticated private-network Paper endpoint. Construction is separate from plugin/runtime registration. */
public final class DiscordCommandBridgeEndpoint implements AutoCloseable {
    private static final int BACKLOG = 16;
    private static final int WORKER_THREADS = 2;
    private static final int MAX_REQUEST_BYTES = 4_096;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    private static final String POST = "POST";

    private final String credential;
    private final CommandBridgeService service;
    private final CommandBridgeRequestAuthenticator authenticator;
    private final CommandBridgeWireCodec codec = new CommandBridgeWireCodec();
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final Logger logger;

    public DiscordCommandBridgeEndpoint(
            String serverId,
            String bindHost,
            int port,
            String credential,
            CommandBridgeService service,
            Logger logger
    ) throws IOException {
        validateConfiguration(serverId, credential, service, logger);
        this.credential = credential;
        this.service = service;
        this.logger = logger;
        this.authenticator = new CommandBridgeRequestAuthenticator(
                credential,
                "discord-command:" + serverId
        );
        HttpServer created = HttpServer.create(bindAddress(bindHost, port), BACKLOG);
        ThreadPoolExecutor workers = workers();
        try {
            created.setExecutor(workers);
            created.createContext(CommandBridgeHttpSigning.REQUEST_PATH, this::handle);
            created.start();
        } catch (RuntimeException failure) {
            created.stop(0);
            workers.shutdownNow();
            throw failure;
        }
        this.server = created;
        this.executor = workers;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String responseNonce = null;
        try {
            if (!exactTarget(exchange)) {
                respondUnsigned(exchange, 404);
                return;
            }
            if (!POST.equals(exchange.getRequestMethod())) {
                respondUnsigned(exchange, 405);
                return;
            }
            if (!privatePeer(exchange.getRemoteAddress().getAddress())) {
                respondUnsigned(exchange, 401);
                return;
            }
            String body = readBody(exchange);
            if (body == null) {
                respondUnsigned(exchange, 413);
                return;
            }
            CommandBridgeRequestAuthenticator.Result authentication = authenticate(exchange, body);
            if (authentication != CommandBridgeRequestAuthenticator.Result.ACCEPTED) {
                respondUnsigned(exchange, 401);
                return;
            }
            responseNonce = exchange.getRequestHeaders().getFirst(CommandBridgeHttpSigning.NONCE_HEADER);
            Optional<CommandBridgeRequest> request = codec.decodeRequest(body);
            if (request.isEmpty()) {
                respondSigned(exchange, 400, invalidRequest(), responseNonce);
                return;
            }
            respondSigned(exchange, 200, service.handle(request.orElseThrow()), responseNonce);
        } catch (RuntimeException failure) {
            log("discord_command_bridge_request_failed", failure);
            if (exchange.getResponseCode() == -1) {
                respondFailure(exchange, responseNonce);
            }
        } finally {
            exchange.close();
        }
    }

    private CommandBridgeRequestAuthenticator.Result authenticate(HttpExchange exchange, String body) {
        return authenticator.authenticate(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                body,
                exchange.getRequestHeaders().getFirst(CommandBridgeHttpSigning.TIMESTAMP_HEADER),
                exchange.getRequestHeaders().getFirst(CommandBridgeHttpSigning.NONCE_HEADER),
                exchange.getRequestHeaders().getFirst(CommandBridgeHttpSigning.SIGNATURE_HEADER)
        );
    }

    private void respondFailure(HttpExchange exchange, String nonce) throws IOException {
        if (nonce == null) {
            respondUnsigned(exchange, 503);
            return;
        }
        respondSigned(
                exchange,
                503,
                CommandBridgeResponse.withoutOutput(
                        CommandBridgeOutcome.INTERNAL_ERROR,
                        "Command bridge request could not be safely processed."
                ),
                nonce
        );
    }

    private void respondSigned(
            HttpExchange exchange,
            int status,
            CommandBridgeResponse response,
            String nonce
    ) throws IOException {
        String body = codec.encodeResponse(response);
        exchange.getResponseHeaders().set(
                CommandBridgeHttpSigning.RESPONSE_SIGNATURE_HEADER,
                authenticator.signResponse(nonce, status, body)
        );
        respond(exchange, status, body);
    }

    private static void respondUnsigned(HttpExchange exchange, int status) throws IOException {
        respond(exchange, status, "");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, encoded.length);
        if (encoded.length > 0) {
            exchange.getResponseBody().write(encoded);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] encoded = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        return encoded.length > MAX_REQUEST_BYTES ? null : new String(encoded, StandardCharsets.UTF_8);
    }

    private static boolean exactTarget(HttpExchange exchange) {
        return CommandBridgeHttpSigning.REQUEST_PATH.equals(exchange.getRequestURI().getRawPath())
                && exchange.getRequestURI().getRawQuery() == null;
    }

    static boolean privatePeer(InetAddress address) {
        return address != null && (address.isLoopbackAddress() || address.isSiteLocalAddress());
    }

    static InetSocketAddress bindAddress(String bindHost, int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("command bridge port is out of range");
        }
        return switch (bindHost) {
            case "127.0.0.1" -> new InetSocketAddress("127.0.0.1", port);
            case "0.0.0.0" -> new InetSocketAddress("0.0.0.0", port);
            default -> throw new IllegalArgumentException("command bridge bind host is unsupported");
        };
    }

    private static void validateConfiguration(
            String serverId,
            String credential,
            CommandBridgeService service,
            Logger logger
    ) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("command bridge server ID is invalid");
        }
        if (credential == null || credential.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("command bridge credential must contain at least 32 characters");
        }
        if (service == null || logger == null) {
            throw new IllegalArgumentException("command bridge endpoint dependencies are required");
        }
    }

    private static CommandBridgeResponse invalidRequest() {
        return CommandBridgeResponse.withoutOutput(
                CommandBridgeOutcome.INVALID_REQUEST,
                "Command request body is invalid."
        );
    }

    private static ThreadPoolExecutor workers() {
        return new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(BACKLOG),
                Thread.ofPlatform().daemon(true).name("discord-command-bridge-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private void log(String code, RuntimeException failure) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, "{0} type={1}", new Object[] {code, failure.getClass().getSimpleName()});
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
