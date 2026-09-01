package net.enthusia.staff.paper.auth;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves the loopback Discord staff authority from environment or a panel-uploaded secret file. */
final class DiscordStaffAuthorityConfiguration {
    static final String FILE_NAME = "discord-staff-authority.properties";
    static final String CREDENTIAL_PROPERTY = "authority.secret";
    static final String URL_PROPERTY = "authority.url";

    private static final int MIN_SECRET_LENGTH = 32;
    private static final String SCHEME = "http";
    private static final String HOST = "127.0.0.1";
    private static final String PATH = "/v1/staff-rank";

    private DiscordStaffAuthorityConfiguration() {
    }

    static Optional<Value> fromRuntime(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return fromSources(plugin.getDataFolder().toPath(), System.getenv());
    }

    static Optional<Value> fromSources(Path dataFolder, Map<String, String> environment) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(environment, "environment");
        if (configured(environment.get(DiscordStaffAuthorityEndpoint.CREDENTIAL_ENV))
                || configured(environment.get(DiscordStaffAuthorityEndpoint.PORT_ENV))) {
            return Optional.of(fromEnvironment(environment));
        }
        Path file = dataFolder.resolve(FILE_NAME);
        return Files.exists(file) ? Optional.of(fromFile(file)) : Optional.empty();
    }

    private static Value fromEnvironment(Map<String, String> environment) {
        String credential = required(
                environment.get(DiscordStaffAuthorityEndpoint.CREDENTIAL_ENV),
                DiscordStaffAuthorityEndpoint.CREDENTIAL_ENV);
        int port = DiscordStaffAuthorityEndpoint.parsePort(
                environment.get(DiscordStaffAuthorityEndpoint.PORT_ENV));
        return new Value(validateCredential(credential), port);
    }

    private static Value fromFile(Path file) {
        Properties properties = load(file);
        String credential = validateCredential(required(properties.getProperty(CREDENTIAL_PROPERTY), CREDENTIAL_PROPERTY));
        URI uri = authorityUri(required(properties.getProperty(URL_PROPERTY), URL_PROPERTY));
        return new Value(credential, uri.getPort());
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Discord staff authority config file is unavailable", exception);
        }
    }

    private static URI authorityUri(String raw) {
        URI uri = parseUri(raw);
        if (!validNetwork(uri) || !validResource(uri)) {
            throw new IllegalArgumentException("authority.url must be the explicit IPv4 loopback authority URL");
        }
        return uri;
    }

    private static URI parseUri(String raw) {
        try {
            return new URI(raw.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("authority.url is invalid", exception);
        }
    }

    private static boolean validNetwork(URI uri) {
        return SCHEME.equalsIgnoreCase(uri.getScheme())
                && HOST.equals(uri.getHost())
                && uri.getPort() >= 1
                && uri.getPort() <= 65_535;
    }

    private static boolean validResource(URI uri) {
        return PATH.equals(uri.getPath())
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null;
    }

    private static String validateCredential(String value) {
        if (value.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("authority credential must contain at least 32 characters");
        }
        return value;
    }

    private static String required(String value, String label) {
        if (!configured(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    record Value(String secret, int port) {
    }
}
