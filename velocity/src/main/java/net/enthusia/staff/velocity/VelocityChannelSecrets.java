package net.enthusia.staff.velocity;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.crypto.SecretKey;
import net.enthusia.staff.common.security.PrivateChannelSecrets;
import net.enthusia.staff.common.security.SecretKeyMaterial;

final class VelocityChannelSecrets {
    static final String PROXY_KEY_PROPERTY = "channel.proxy-secret";
    static final String TLS_STORE_PROPERTY = "channel.tls-store-password";
    private static final String BACKEND_PREFIX = "channel.backend.";
    private static final String BACKEND_SUFFIX = ".secret";

    private VelocityChannelSecrets() {
    }

    static Loaded load(
            VelocityConfiguration configuration,
            Path dataDirectory,
            Function<String, String> environment
    ) {
        Objects.requireNonNull(configuration, "configuration");
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put(PROXY_KEY_PROPERTY, configuration.channelProxySecretEnvironment());
        sources.put(TLS_STORE_PROPERTY, configuration.channelTlsKeyStorePasswordEnvironment());
        configuration.backendSecretEnvironments().forEach((serverId, variable) ->
                sources.put(backendProperty(serverId), variable));

        Map<String, String> values = PrivateChannelSecrets.load(dataDirectory, sources, environment);
        LinkedHashMap<String, SecretKey> backends = new LinkedHashMap<>();
        configuration.backendSecretEnvironments().keySet().forEach(serverId -> backends.put(
                serverId,
                SecretKeyMaterial.hmacSha256FromBase64(values.get(backendProperty(serverId)).trim())
        ));
        return new Loaded(
                Map.copyOf(backends),
                SecretKeyMaterial.hmacSha256FromBase64(values.get(PROXY_KEY_PROPERTY).trim()),
                values.get(TLS_STORE_PROPERTY).toCharArray()
        );
    }

    static String backendProperty(String serverId) {
        return BACKEND_PREFIX + serverId + BACKEND_SUFFIX;
    }

    record Loaded(Map<String, SecretKey> backendKeys, SecretKey proxyKey, char[] tlsStorePassword) {
        Loaded {
            backendKeys = Map.copyOf(backendKeys);
            Objects.requireNonNull(proxyKey, "proxyKey");
            Objects.requireNonNull(tlsStorePassword, "tlsStorePassword");
        }
    }
}
