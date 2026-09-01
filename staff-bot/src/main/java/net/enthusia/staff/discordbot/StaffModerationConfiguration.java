package net.enthusia.staff.discordbot;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.persistence.DatabaseConfig;

/** Optional read-only moderation configuration sourced from a validated runtime map or file. */
final class StaffModerationConfiguration {
    static final String JDBC_URL_ENV = "ENTHUSIA_STAFF_BOT_DB_JDBC_URL";
    static final String DB_USERNAME_ENV = "ENTHUSIA_STAFF_BOT_DB_USERNAME";
    static final String DB_CREDENTIAL_ENV = "ENTHUSIA_STAFF_BOT_DB_PASSWORD";
    static final String AUTHORITY_URL_ENV = "ENTHUSIA_STAFF_BOT_AUTHORITY_URL";
    static final String AUTHORITY_CREDENTIAL_ENV = "ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET";
    static final String COMPONENT_SIGNING_ENV = "ENTHUSIA_STAFF_BOT_COMPONENT_SECRET";
    static final String DB_POOL_SIZE_ENV = "ENTHUSIA_STAFF_BOT_DB_POOL_SIZE";
    static final String DB_TIMEOUT_MILLIS_ENV = "ENTHUSIA_STAFF_BOT_DB_TIMEOUT_MILLIS";

    private static final int DEFAULT_POOL_SIZE = 4;
    private static final int MIN_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 16;
    private static final int DEFAULT_TIMEOUT_MILLIS = 3_000;
    private static final int MIN_TIMEOUT_MILLIS = 250;
    private static final int MAX_TIMEOUT_MILLIS = 60_000;
    private static final int MIN_CRYPTO_SECRET_LENGTH = 32;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;
    private static final String HTTP_SCHEME = "http";
    private static final String AUTHORITY_HOST = "127.0.0.1";
    private static final String AUTHORITY_PATH = "/v1/staff-rank";
    private static final List<String> REQUIRED = List.of(
            JDBC_URL_ENV,
            DB_USERNAME_ENV,
            DB_CREDENTIAL_ENV,
            AUTHORITY_URL_ENV,
            AUTHORITY_CREDENTIAL_ENV,
            COMPONENT_SIGNING_ENV
    );

    private final DatabaseConfig databaseConfig;
    private final URI authorityEndpoint;
    private final String authorityCredential;
    private final String componentSigningSecret;

    private StaffModerationConfiguration(
            DatabaseConfig database,
            URI authorityUri,
            String authoritySecret,
            String componentSecret
    ) {
        this.databaseConfig = Objects.requireNonNull(database, "database");
        this.authorityEndpoint = Objects.requireNonNull(authorityUri, "authorityUri");
        this.authorityCredential = cryptoSecret(authoritySecret, AUTHORITY_CREDENTIAL_ENV);
        this.componentSigningSecret = cryptoSecret(componentSecret, COMPONENT_SIGNING_ENV);
    }

    static Optional<StaffModerationConfiguration> fromSystemEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static StaffModerationConfiguration fromFile(Path path) {
        return fromEnvironment(StaffModerationConfigFile.read(path))
                .orElseThrow(() -> new IllegalArgumentException("moderation config file is empty"));
    }

    static Optional<StaffModerationConfiguration> fromEnvironment(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        long configured = REQUIRED.stream().filter(envName -> present(values.get(envName))).count();
        if (configured == 0) {
            return Optional.empty();
        }
        if (configured != REQUIRED.size()) {
            throw new IllegalArgumentException("read-only staff moderation configuration is incomplete");
        }
        int poolSize = integer(values, DB_POOL_SIZE_ENV, DEFAULT_POOL_SIZE, MIN_POOL_SIZE, MAX_POOL_SIZE);
        int timeout = integer(
                values,
                DB_TIMEOUT_MILLIS_ENV,
                DEFAULT_TIMEOUT_MILLIS,
                MIN_TIMEOUT_MILLIS,
                MAX_TIMEOUT_MILLIS
        );
        DatabaseConfig database = new DatabaseConfig(
                values.get(JDBC_URL_ENV).trim(),
                values.get(DB_USERNAME_ENV).trim(),
                required(values.get(DB_CREDENTIAL_ENV), DB_CREDENTIAL_ENV),
                poolSize,
                timeout
        );
        return Optional.of(new StaffModerationConfiguration(
                database,
                authorityUri(values.get(AUTHORITY_URL_ENV)),
                values.get(AUTHORITY_CREDENTIAL_ENV),
                values.get(COMPONENT_SIGNING_ENV)
        ));
    }

    DatabaseConfig database() {
        return databaseConfig;
    }

    URI authorityUri() {
        return authorityEndpoint;
    }

    String authoritySecret() {
        return authorityCredential;
    }

    String componentSecret() {
        return componentSigningSecret;
    }

    @Override
    public String toString() {
        return "StaffModerationConfiguration[authorityUri=%s, database=<redacted>, authoritySecret=<redacted>, componentSecret=<redacted>]"
                .formatted(authorityEndpoint);
    }

    private static URI authorityUri(String raw) {
        URI uri = parseUri(raw);
        if (!validAuthorityNetwork(uri) || !validAuthorityResource(uri)) {
            throw new IllegalArgumentException("staff authority endpoint must be an explicit loopback HTTP URL");
        }
        return uri;
    }

    private static URI parseUri(String raw) {
        try {
            return new URI(raw.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("staff authority endpoint URL is invalid", exception);
        }
    }

    private static boolean validAuthorityNetwork(URI uri) {
        return HTTP_SCHEME.equalsIgnoreCase(uri.getScheme())
                && AUTHORITY_HOST.equals(uri.getHost())
                && uri.getPort() >= MIN_PORT
                && uri.getPort() <= MAX_PORT;
    }

    private static boolean validAuthorityResource(URI uri) {
        return AUTHORITY_PATH.equals(uri.getPath())
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null;
    }

    private static int integer(Map<String, String> values, String envName, int fallback, int min, int max) {
        String raw = values.get(envName);
        if (!present(raw)) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(envName + " is outside its safe range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(envName + " must be an integer", exception);
        }
    }

    private static String cryptoSecret(String value, String envName) {
        String secret = required(value, envName);
        if (secret.length() < MIN_CRYPTO_SECRET_LENGTH) {
            throw new IllegalArgumentException(envName + " must contain at least 32 characters");
        }
        return secret;
    }

    private static String required(String value, String envName) {
        if (!present(value)) {
            throw new IllegalArgumentException(envName + " is required");
        }
        return value;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
