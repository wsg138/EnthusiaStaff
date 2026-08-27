package net.enthusia.staff.paper.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional loopback-only authority bridge for the isolated Discord staff bot.
 * Rank is calculated from current LuckPerms data on every request; Discord roles are never inputs.
 */
public final class DiscordStaffAuthorityEndpoint implements AutoCloseable {
    public static final String SECRET_KEY = "ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET";
    public static final String PORT_KEY = "ENTHUSIA_STAFF_DISCORD_AUTHORITY_PORT";

    private static final int DEFAULT_PORT = 8771;
    private static final int BACKLOG = 16;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final String PATH = "/v1/staff-rank";

    private final JavaPlugin plugin;
    private final String bearer;
    private final LuckPerms luckPerms;
    private final HttpServer server;
    private final ExecutorService executor;

    private DiscordStaffAuthorityEndpoint(
            JavaPlugin plugin,
            String secret,
            int port,
            LuckPerms luckPerms
    ) throws IOException {
        this.plugin = plugin;
        this.bearer = "Bearer " + secret;
        this.luckPerms = luckPerms;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), BACKLOG);
        this.executor = Executors.newFixedThreadPool(
                2,
                Thread.ofPlatform().daemon(true).name("discord-staff-authority-", 0).factory()
        );
        server.setExecutor(executor);
        server.createContext(PATH, this::handle);
        server.start();
    }

    public static Optional<DiscordStaffAuthorityEndpoint> startIfConfigured(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must be present");
        }
        String secret = System.getenv(SECRET_KEY);
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            log(plugin, "discord_staff_authority_secret_too_short", null);
            return Optional.empty();
        }
        int port;
        try {
            port = parsePort(System.getenv(PORT_KEY));
        } catch (IllegalArgumentException exception) {
            log(plugin, "discord_staff_authority_invalid_port", exception);
            return Optional.empty();
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            log(plugin, "discord_staff_authority_luckperms_absent", null);
            return Optional.empty();
        }
        final LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            log(plugin, "discord_staff_authority_luckperms_unavailable", exception);
            return Optional.empty();
        }
        try {
            return Optional.of(new DiscordStaffAuthorityEndpoint(plugin, secret, port, luckPerms));
        } catch (IOException exception) {
            log(plugin, "discord_staff_authority_bind_failed", exception);
            return Optional.empty();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "");
                return;
            }
            if (!authorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
                respond(exchange, 401, "");
                return;
            }
            UUID playerId = playerId(exchange.getRequestURI().getRawQuery());
            if (playerId == null) {
                respond(exchange, 400, "");
                return;
            }
            Optional<StaffRank> rank = resolve(playerId);
            if (rank.isEmpty()) {
                respond(exchange, 404, "");
                return;
            }
            respond(exchange, 200, rank.orElseThrow().name());
        } catch (RuntimeException exception) {
            log(plugin, "discord_staff_authority_request_failed", exception);
            if (exchange.getResponseCode() == -1) {
                respond(exchange, 503, "");
            }
        } finally {
            exchange.close();
        }
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

    private boolean authorized(String supplied) {
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                bearer.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
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

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, encoded.length);
        if (encoded.length > 0) {
            exchange.getResponseBody().write(encoded);
        }
    }

    static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
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
            plugin.getLogger().log(Level.WARNING, code + " type=" + failure.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
