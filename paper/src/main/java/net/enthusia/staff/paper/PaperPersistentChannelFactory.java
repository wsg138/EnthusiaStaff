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

    static Settings snapshot(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new Settings(
                plugin.getConfig().getBoolean("channel.enabled", false),
                plugin.getConfig().getString("network.server-id", "SMP"),
                plugin.getConfig().getString("channel.host", "127.0.0.1"),
                plugin.getConfig().getInt("channel.port", 28_765),
                plugin.getConfig().getString("channel.proxy-id", "VELOCITY"),
                plugin.getConfig().getString("channel.backend-secret-environment"),
                plugin.getConfig().getString("channel.proxy-secret-environment"),
                plugin.getConfig().getString("channel.tls.trust-store", "channel-trust.p12"),
                plugin.getConfig().getString(
                        "channel.tls.trust-store-password-environment",
                        "ES_CHANNEL_TLS_TRUSTSTORE_PASSWORD"
                ),
                plugin.getDataFolder().toPath().toAbsolutePath().normalize()
        );
    }

    static Optional<PersistentChannelClient> start(
            Settings settings,
            BiFunction<String, ProtocolEnvelope, Boolean> messageHandler,
            Consumer<String> connectionState
    ) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(messageHandler, "messageHandler");
        Objects.requireNonNull(connectionState, "connectionState");
        if (!settings.enabled()) {
            return Optional.empty();
        }
        ChannelConfiguration loaded = loadConfiguration(settings);
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

    private static ChannelConfiguration loadConfiguration(Settings settings) {
        return new ChannelConfiguration(
                settings.backendId(),
                new PersistentChannelClient.Configuration(
                        settings.backendId(),
                        settings.host(),
                        settings.port(),
                        secretFromEnvironment(settings.backendSecretEnvironment()),
                        settings.proxyId(),
                        secretFromEnvironment(settings.proxySecretEnvironment()),
                        clientTlsContext(settings)
                )
        );
    }

    private static SecretKey secretFromEnvironment(String environment) {
        return SecretKeyMaterial.hmacSha256FromBase64(System.getenv(environment));
    }

    private static SSLContext clientTlsContext(Settings settings) {
        char[] password = passwordFromEnvironment(settings.trustStorePasswordEnvironment());
        try {
            Path resolved = resolveChannelTlsPath(settings.dataDirectory(), Path.of(settings.trustStore()));
            return TlsContextLoader.client(resolved, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static Path resolveChannelTlsPath(Path dataDirectory, Path configured) {
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
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

    record Settings(
            boolean enabled,
            String backendId,
            String host,
            int port,
            String proxyId,
            String backendSecretEnvironment,
            String proxySecretEnvironment,
            String trustStore,
            String trustStorePasswordEnvironment,
            Path dataDirectory
    ) {
        Settings {
            Objects.requireNonNull(dataDirectory, "dataDirectory");
            if (!enabled) {
                return;
            }
            backendId = requireText(backendId, "backendId");
            host = requireText(host, "host");
            proxyId = requireText(proxyId, "proxyId");
            backendSecretEnvironment = requireText(
                    backendSecretEnvironment,
                    "backendSecretEnvironment"
            );
            proxySecretEnvironment = requireText(proxySecretEnvironment, "proxySecretEnvironment");
            trustStore = requireText(trustStore, "trustStore");
            trustStorePasswordEnvironment = requireText(
                    trustStorePasswordEnvironment,
                    "trustStorePasswordEnvironment"
            );
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("Persistent channel port must be between 1 and 65535");
            }
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Persistent channel " + field + " is required");
            }
            return value;
        }
    }

    private record ChannelConfiguration(
            String backendId,
            PersistentChannelClient.Configuration client
    ) {
    }
}
