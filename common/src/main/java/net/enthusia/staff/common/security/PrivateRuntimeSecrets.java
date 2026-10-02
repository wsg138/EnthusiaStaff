package net.enthusia.staff.common.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/** Resolves startup secrets on hosts without configurable process environments. */
public final class PrivateRuntimeSecrets {
    private static final int MAXIMUM_FILE_BYTES = 16_384;
    private static final String RUNTIME_FILE_NAME = "secrets.properties";

    private PrivateRuntimeSecrets() { }

    public static String required(Path directory, String name, Function<String, String> environment) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(environment, "environment");
        validateName(name);
        String environmentValue = environment.apply(name);
        if (present(environmentValue)) {
            return environmentValue;
        }
        return requiredProperty(load(directory.resolve(RUNTIME_FILE_NAME)), name);
    }

    private static void validateName(String name) {
        if (name == null || !name.matches("[A-Z][A-Z0-9_]{0,127}")) {
            throw new IllegalArgumentException("Invalid runtime secret name");
        }
    }

    private static Properties load(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Private runtime secret file is missing");
        }
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] contents = input.readNBytes(MAXIMUM_FILE_BYTES + 1);
            if (contents.length > MAXIMUM_FILE_BYTES) {
                throw new IllegalStateException("Private runtime secret file is too large");
            }
            Properties secrets = new Properties();
            secrets.load(new ByteArrayInputStream(contents));
            return secrets;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Private runtime secret file cannot be read");
        }
    }

    private static String requiredProperty(Properties secrets, String name) {
        String value = secrets.getProperty(name);
        if (!present(value)) {
            throw new IllegalStateException("Required private runtime secret is missing");
        }
        return value;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
