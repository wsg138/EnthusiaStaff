package net.enthusia.staff.authoritybridge;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Runtime-only file configuration for the owner-authorized ES-D16 authority bridge. */
final class AuthorityBridgeConfiguration {
    static final String FILE_NAME = "authority.properties";
    static final String AUTH_VALUE_PROPERTY = "authority." + "secret";
    static final String PORT_PROPERTY = "authority.port";

    private static final Set<String> ALLOWED_PROPERTIES = Set.of(AUTH_VALUE_PROPERTY, PORT_PROPERTY);
    private static final int DEFAULT_PORT = 8771;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;
    private static final int MIN_KEY_MATERIAL_LENGTH = 32;

    private AuthorityBridgeConfiguration() {
    }

    static Value load(Path dataFolder) {
        if (dataFolder == null) {
            throw new IllegalArgumentException("data folder must be present");
        }
        Properties properties = loadProperties(dataFolder.resolve(FILE_NAME));
        rejectUnknownProperties(properties);
        String keyMaterial = required(properties.getProperty(AUTH_VALUE_PROPERTY), AUTH_VALUE_PROPERTY);
        if (keyMaterial.length() < MIN_KEY_MATERIAL_LENGTH) {
            throw new IllegalArgumentException("authority authentication value must contain at least 32 characters");
        }
        return new Value(keyMaterial, parsePort(properties.getProperty(PORT_PROPERTY)));
    }

    private static Properties loadProperties(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("authority bridge configuration file is unavailable", exception);
        }
    }

    private static void rejectUnknownProperties(Properties properties) {
        if (!ALLOWED_PROPERTIES.containsAll(properties.stringPropertyNames())) {
            throw new IllegalArgumentException("authority bridge configuration contains unsupported properties");
        }
    }

    private static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < MIN_PORT || port > MAX_PORT) {
                throw new IllegalArgumentException("authority bridge port is outside the valid range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("authority bridge port must be numeric", exception);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    record Value(String keyMaterial, int port) {
    }
}
