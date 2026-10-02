package net.enthusia.staff.common.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Loads channel-only secret material from one complete source without ever logging values.
 *
 * <p>Environment variables remain the preferred source. If none of the configured channel
 * environment variables has a value, {@value #FILE_NAME} in the runtime data directory is
 * used instead. A partially populated environment source fails closed rather than mixing
 * environment and file material.</p>
 */
public final class PrivateChannelSecrets {
    public static final String FILE_NAME = "channel.properties";
    private static final int MAXIMUM_FILE_BYTES = 16_384;

    private PrivateChannelSecrets() {
    }

    public static Map<String, String> load(
            Path dataDirectory,
            Map<String, String> propertyToEnvironment,
            Function<String, String> environment
    ) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(propertyToEnvironment, "propertyToEnvironment");
        Objects.requireNonNull(environment, "environment");
        if (propertyToEnvironment.isEmpty()) {
            throw new IllegalArgumentException("At least one channel secret must be configured");
        }

        LinkedHashMap<String, String> environmentValues = new LinkedHashMap<>();
        int present = 0;
        for (Map.Entry<String, String> entry : propertyToEnvironment.entrySet()) {
            String property = requireName(entry.getKey(), "channel secret property");
            String variable = requireName(entry.getValue(), "channel secret environment variable");
            String value = environment.apply(variable);
            if (isPresent(value)) {
                environmentValues.put(property, value);
                present++;
            }
        }
        if (present == propertyToEnvironment.size()) {
            return Map.copyOf(environmentValues);
        }
        if (present != 0) {
            throw new IllegalStateException("Channel secret environment configuration is incomplete");
        }
        return fromFile(dataDirectory, propertyToEnvironment.keySet());
    }

    private static Map<String, String> fromFile(Path dataDirectory, Set<String> expectedProperties) {
        Path base = dataDirectory.toAbsolutePath().normalize();
        Path file = base.resolve(FILE_NAME).normalize();
        Properties properties = readProperties(file);
        if (!properties.stringPropertyNames().equals(expectedProperties)) {
            throw new IllegalStateException("Private channel.properties entries do not match the configured channel");
        }

        LinkedHashMap<String, String> loaded = new LinkedHashMap<>();
        for (String property : expectedProperties) {
            loaded.put(property, requireSecret(properties.getProperty(property)));
        }
        return Map.copyOf(loaded);
    }

    private static Properties readProperties(Path file) {
        try (SeekableByteChannel channel = Files.newByteChannel(
                file,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        ); InputStream input = Channels.newInputStream(channel)) {
            if (channel.size() > MAXIMUM_FILE_BYTES) {
                throw new IllegalStateException("Private channel.properties file is too large");
            }
            byte[] contents = readBounded(input);
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(contents));
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Private channel.properties file cannot be read", exception);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] contents = input.readNBytes(MAXIMUM_FILE_BYTES + 1);
        if (contents.length > MAXIMUM_FILE_BYTES) {
            throw new IllegalStateException("Private channel.properties file is too large");
        }
        return contents;
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured");
        }
        return value.trim();
    }

    private static String requireSecret(String value) {
        if (!isPresent(value)) {
            throw new IllegalStateException("Private channel.properties contains an empty required value");
        }
        return value;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
