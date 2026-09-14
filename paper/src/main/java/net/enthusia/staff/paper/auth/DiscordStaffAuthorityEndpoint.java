package net.enthusia.staff.paper.auth;

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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional authority bridge for the isolated Discord staff bot.
 * Rank is calculated from current LuckPerms data on every request; Discord roles are never inputs.
 */
public final class DiscordStaffAuthorityEndpoint implements AutoCloseable {
    public static final String CREDENTIAL_ENV = "ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET";
    public static final String PORT_ENV = "ENTHUSIA_STAFF_DISCORD_AUTHORITY_PORT";

    private static final int DEFAULT_PORT = 8771;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;
    private static final int BACKLOG = 16;
    private static final int WORKER_THREADS = 2;
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    private static final String PATH = "/v1/staff-rank";
    private static final String GET_METHOD = "GET";

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final DiscordStaffAuthorityAuthenticator authenticator;

    private DiscordStaffAuthorityEndpoint(
            JavaPlugin plugin,
            DiscordStaffAuthorityConfiguration.Value configuration,
            LuckPerms luckPerms
    ) throws IOException {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.authenticator = new DiscordStaffAuthorityAuthenticator(
                configuration.secret(), configuration.privateSplit());
        HttpServer createdServer = HttpServer.create(
                bindAddress(configuration.bindHost(), configuration.port()), BACKLOG);
        ThreadPoolExecutor createdExecutor = new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(BACKLOG),
                Thread.ofPlatform().daemon(true).name("discord-staff-authority-", 0).factory(),
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

    public static Optional<DiscordStaffAuthorityEndpoint> startIfConfigured(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must be present");
        }
        Optional<DiscordStaffAuthorityConfiguration.Value> configuration = configuredAuthority(plugin);
        Optional<LuckPerms> luckPerms = configuredLuckPerms(plugin);
        if (configuration.isEmpty() || luckPerms.isEmpty()) {
            return Optional.empty();
        }
        return bind(plugin, configuration.orElseThrow(), luckPerms.orElseThrow());
    }

    private static Optional<DiscordStaffAuthorityConfiguration.Value> configuredAuthority(JavaPlugin plugin) {
        try {
            return DiscordStaffAuthorityConfiguration.fromRuntime(plugin);
        } catch (IllegalArgumentException exception) {
            log(plugin, "discord_staff_authority_configuration_invalid", exception);
            return Optional.empty();
        }
    }

    private static Optional<LuckPerms> configuredLuckPerms(JavaPlugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            log(plugin, "discord_staff_authority_luckperms_absent", null);
            return Optional.empty();
        }
        try {
            return Optional.of(LuckPermsProvider.get());
        } catch (IllegalStateException exception) {
            log(plugin, "discord_staff_authority_luckperms_unavailable", exception);
            return Optional.empty();
        }
    }

    private static Optional<DiscordStaffAuthorityEndpoint> bind(
            JavaPlugin plugin,
            DiscordStaffAuthorityConfiguration.Value configuration,
            LuckPerms luckPerms
    ) {
        try {
            return Optional.of(new DiscordStaffAuthorityEndpoint(plugin, configuration, luckPerms));
        } catch (IOException | RuntimeException exception) {
            log(plugin, "discord_staff_authority_bind_failed", exception);
            return Optional.empty();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        DiscordStaffAuthorityAuthenticator.Result authorization = null;
        try {
            if (!GET_METHOD.equals(exchange.getRequestMethod())) {
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
            log(plugin, "discord_staff_authority_request_failed", exception);
            if (exchange.getResponseCode() == -1) {
                respond(exchange, 503, "", authorization);
            }
        } finally {
            exchange.close();
        }
    }

    private DiscordStaffAuthorityAuthenticator.Result authenticate(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        String target = exchange.getRequestURI().getRawPath()
                + (rawQuery == null ? "" : "?" + rawQuery);
        return authenticator.authenticate(
                exchange.getRemoteAddress().getAddress(),
                exchange.getRequestMethod(),
                target,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.TIMESTAMP_HEADER),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.NONCE_HEADER),
                exchange.getRequestHeaders().getFirst(StaffAuthorityHttpSigning.SIGNATURE_HEADER)
        );
    }

    private Optional<StaffRank> resolve(UUID playerId) {
        try {
            User user = luckPerms.getUserManager().loadUser(playerId)
                    .get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return PaperStaffRankResolver.resolve(permission -> user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("staff authority lookup interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("staff authority lookup unavailable", exception);
        }
    }

    private static UUID playerId(String rawQuery) {
        if (rawQuery == null || !rawQuery.startsWith("player=") || rawQuery.indexOf('&') >= 0) {
            return null;
        }
        try {
            return UUID.fromString(URLDecoder.decode(rawQuery.substring("player=".length()), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body,
            DiscordStaffAuthorityAuthenticator.Result authorization
    ) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (authorization != null) {
            String signature = authenticator.responseSignature(authorization, status, body);
            if (signature != null) {
                exchange.getResponseHeaders().set(StaffAuthorityHttpSigning.RESPONSE_SIGNATURE_HEADER, signature);
            }
        }
        exchange.sendResponseHeaders(status, encoded.length);
        if (encoded.length > 0) {
            exchange.getResponseBody().write(encoded);
        }
    }

    static InetSocketAddress bindAddress(String host, int port) {
        return switch (host) {
            case "127.0.0.1" -> new InetSocketAddress("127.0.0.1", port);
            case "0.0.0.0" -> new InetSocketAddress("0.0.0.0", port);
            default -> throw new IllegalArgumentException("authority bind host is unsupported");
        };
    }

    static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException("authority port out of range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("authority port must be numeric", exception);
        }
    }

    private static void log(JavaPlugin plugin, String code, Throwable failure) {
        if (!plugin.getLogger().isLoggable(Level.WARNING)) {
            return;
        }
        if (failure == null) {
            plugin.getLogger().warning(code);
        } else {
            plugin.getLogger().log(
                    Level.WARNING,
                    "{0} type={1}",
                    new Object[] {code, failure.getClass().getSimpleName()}
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
