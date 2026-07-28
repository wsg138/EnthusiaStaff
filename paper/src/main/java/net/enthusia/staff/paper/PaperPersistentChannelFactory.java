package net.enthusia.staff.paper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import net.enthusia.staff.protocol.PersistentChannelClient;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import net.enthusia.staff.protocol.TlsContextLoader;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperPersistentChannelFactory {
    private PaperPersistentChannelFactory() {
    }

    static Optional<PersistentChannelClient> start(
            JavaPlugin plugin,
            BiFunction<String, ProtocolEnvelope, Boolean> messageHandler,
            Consumer<String> connectionState
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(messageHandler, "messageHandler");
        Objects.requireNonNull(connectionState, "connectionState");
        if (!plugin.getConfig().getBoolean("channel.enabled", false)) {
            plugin.getLogger().warning(
                    "Persistent Velocity channel is disabled; new punishment writes remain disabled"
            );
            return Optional.empty();
        }
        ChannelConfiguration loaded = loadConfiguration(plugin);
        PersistentChannelClient client = new PersistentChannelClient(
                loaded.client(),
                Clock.systemUTC(),
                envelope -> messageHandler.apply(loaded.backendId(), envelope),
                connectionState
        );
        try {
            client.start();
            return Optional.of(client);
        } catch (RuntimeException exception) {
            try {
                client.close();
            } catch (RuntimeException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private static ChannelConfiguration loadConfiguration(JavaPlugin plugin) {
        String backendId = plugin.getConfig().getString("network.server-id", "SMP");
        String proxyId = plugin.getConfig().getString("channel.proxy-id", "VELOCITY");
        String backendEnvironment = plugin.getConfig().getString("channel.backend-secret-environment");
        String proxyEnvironment = plugin.getConfig().getString("channel.proxy-secret-environment");
        String trustStore = plugin.getConfig().getString("channel.tls.trust-store", "channel-trust.p12");
        String trustStorePasswordEnvironment = plugin.getConfig().getString(
                "channel.tls.trust-store-password-environment",
                "ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD"
        );
        if (backendId == null || proxyId == null || backendEnvironment == null || proxyEnvironment == null
                || trustStore == null || trustStorePasswordEnvironment == null) {
            throw new IllegalArgumentException("Persistent channel identifiers and secret environments are required");
        }
        return new ChannelConfiguration(
                backendId,
                new PersistentChannelClient.Configuration(
                        backendId,
                        plugin.getConfig().getString("channel.host", "127.0.0.1"),
                        plugin.getConfig().getInt("channel.port", 28_765),
                        secretFromEnvironment(backendEnvironment),
                        proxyId,
                        secretFromEnvironment(proxyEnvironment),
                        clientTlsContext(plugin, trustStore, trustStorePasswordEnvironment)
                )
        );
    }

    private static SecretKey secretFromEnvironment(String environment) {
        return SecretKeyMaterial.hmacSha256FromBase64(System.getenv(environment));
    }

    private static SSLContext clientTlsContext(
            JavaPlugin plugin,
            String configuredPath,
            String passwordEnvironment
    ) {
        char[] password = passwordFromEnvironment(passwordEnvironment);
        try {
            Path resolved = resolveChannelTlsPath(plugin, Path.of(configuredPath));
            return TlsContextLoader.client(resolved, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static Path resolveChannelTlsPath(JavaPlugin plugin, Path configured) {
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path resolved = dataDirectory.resolve(configured).normalize();
        if (!resolved.startsWith(dataDirectory)) {
            throw new IllegalArgumentException("A relative channel TLS path must remain in the plugin data directory");
        }
        return resolved;
    }

    private static char[] passwordFromEnvironment(String environment) {
        String value = System.getenv(environment);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("A required channel TLS store password environment variable is missing");
        }
        return value.toCharArray();
    }

    private record ChannelConfiguration(
            String backendId,
            PersistentChannelClient.Configuration client
    ) {
    }
}
