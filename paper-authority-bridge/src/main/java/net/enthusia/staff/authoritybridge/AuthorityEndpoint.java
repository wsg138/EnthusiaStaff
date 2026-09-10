package net.enthusia.staff.authoritybridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;

/**
 * Minimal ES-D16 acceptance endpoint. It has no commands, listeners, database access, or moderation mutations.
 */
final class AuthorityEndpoint implements AutoCloseable {
    static final String PATH = "/v1/staff-rank";

    private static final String GET_METHOD = "GET";
    private static final String BIND_HOST = "0.0.0.0";
    private static final int BACKLOG = 16;
    private static final int WORKER_THREADS = 2;
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private final LuckPerms luckPerms;
    private final Logger logger;
    private final AuthorityRequestAuthenticator authenticator;
    private final HttpServer server;
    private final ThreadPoolExecutor executor;

    private AuthorityEndpoint(
            AuthorityBridgeConfiguration.Value configuration,
            LuckPerms luckPerms,
            Logger logger
    ) throws IOException {
        this.luckPerms = luckPerms;
        this.logger = logger;
        this.authenticator = new AuthorityRequestAuthenticator(configuration.keyMaterial());
        HttpServer createdServer = HttpServer.create(
                new InetSocketAddress(BIND_HOST, configuration.port()),
                BACKLOG
        );
        ThreadPoolExecutor createdExecutor = new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(BACKLOG),
                Thread.ofPlatform().daemon(true).name("enthusia-authority-bridge-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            createdServer.setExecutor(createdExecutor);
            createdServer.createContext(PATH, this::handle);
            createdServer.start();
        } catch (RuntimeException exception) {
            createdServer.stop(0);
            createdExecutor.shutdownNow();
            throw exception;
        }
        this.server = createdServer;
        this.executor = createdExecutor;
    }

    static AuthorityEndpoint start(
            AuthorityBridgeConfiguration.Value configuration,
            LuckPerms luckPerms,
            Logger logger
    ) throws IOException {
        if (configuration == null || luckPerms == null || logger == null) {
            throw new IllegalArgumentException("authority endpoint dependencies must be present");
        }
        return new AuthorityEndpoint(configuration, luckPerms, logger);
    }

    private void handle(HttpExchange exchange) throws IOException {
        AuthorityRequestAuthenticator.Result authorization = null;
        try {
            if (!GET_METHOD.equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", GET_METHOD);
                respond(exchange, 405, "", null);
                return;
            }
            authorization = authenticate(exchange);
            if (!authorization.accepted()) {
                respond(exchange, 401, "", null);
                return;
            }
            UUID playerId = playerId(exchange.getRequestURI().getRawQuery());
            if (playerId == null) {
                respond(exchange, 400, "", authorization);
                return;
            }
            Optional<StaffRank> rank = resolve(playerId);
            if (rank.isEmpty()) {
                respond(exchange, 404, "", authorization);
                return;
            }
            respond(exchange, 200, rank.orElseThrow().name(), authorization);
        } catch (RuntimeException exception) {
            logFailure(exception);
            respond(exchange, 503, "", authorization);
        } finally {
            exchange.close();
        }
    }

    private AuthorityRequestAuthenticator.Result authenticate(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        String target = exchange.getRequestURI().getRawPath()
                + (rawQuery == null ? "" : "?" + rawQuery);
        return authenticator.authenticate(
                exchange.getRemoteAddress().getAddress(),
                exchange.getRequestMethod(),
                target,
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.TIMESTAMP_HEADER),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.NONCE_HEADER),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.SIGNATURE_HEADER)
        );
    }

    private Optional<StaffRank> resolve(UUID playerId) {
        try {
            User user = luckPerms.getUserManager().loadUser(playerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return AuthorityRankResolver.resolve(permission -> user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("authority lookup interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("authority lookup unavailable", exception);
        }
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body,
            AuthorityRequestAuthenticator.Result authorization
    ) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String responseSignature = authenticator.signResponse(authorization, status, body);
        if (responseSignature != null) {
            exchange.getResponseHeaders().set(
                    StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER,
                    responseSignature
            );
        }
        exchange.sendResponseHeaders(status, encoded.length);
        if (encoded.length > 0) {
            exchange.getResponseBody().write(encoded);
        }
    }

    private static UUID playerId(String rawQuery) {
        if (rawQuery == null || !rawQuery.startsWith("player=") || rawQuery.indexOf('&') >= 0) {
            return null;
        }
        try {
            return UUID.fromString(URLDecoder.decode(
                    rawQuery.substring("player=".length()),
                    StandardCharsets.UTF_8
            ));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void logFailure(RuntimeException exception) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(
                    Level.WARNING,
                    "enthusiastaff_authority_bridge_request_failed type={0}",
                    exception.getClass().getSimpleName()
            );
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
