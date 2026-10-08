package net.enthusia.staff.discordbot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Optional private bridge-only settings for hosts that expose file-backed startup arguments
 * but do not allow adding custom process environment variables.
 *
 * <p>The file is never activated unless explicitly selected by the CLI option. It cannot
 * override the existing moderation bot token, environment identity or other runtime settings.</p>
 */
final class StaffBotChatSettingsFile {
    private static final int MAX_BYTES = 16_384;
    private static final Set<String> ALLOWED_KEYS = Set.of(
            StaffBotChatBridgeConfiguration.MODE_ENV,
            StaffBotChatBridgeConfiguration.ENABLED_ENV,
            StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV,
            StaffBotChatBridgeConfiguration.CUTOVER_ACK_ENV,
            StaffBotChatBridgeConfiguration.HOST_ENV,
            StaffBotChatBridgeConfiguration.PORT_ENV,
            StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV,
            StaffBotChatBridgeConfiguration.PROXY_HMAC_ENV,
            StaffBotChatBridgeConfiguration.TRUST_STORE_ENV,
            StaffBotChatBridgeConfiguration.TRUST_STORE_ACCESS_ENV,
            StaffBotChatBridgeConfiguration.ROUTES_ENV,
            StaffBotChatBridgeConfiguration.INGRESS_ROUTES_ENV,
            StaffBotChatBridgeConfiguration.QUEUE_CAPACITY_ENV,
            StaffBotChatBridgeConfiguration.DEDUPE_CAPACITY_ENV,
            PublicChatDiscordConfiguration.TOKEN_ENV,
            PublicChatDiscordConfiguration.APPLICATION_ID_ENV);

    private StaffBotChatSettingsFile() {
    }

    static Map<String, String> overlay(Path file, Map<String, String> processEnvironment) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(processEnvironment, "processEnvironment");
        Properties values = read(file);
        if (values.isEmpty() || !values.containsKey(StaffBotChatBridgeConfiguration.MODE_ENV)) {
            throw new IllegalArgumentException("private chat settings require an explicit bridge mode");
        }
        if (!ALLOWED_KEYS.containsAll(values.stringPropertyNames())) {
            throw new IllegalArgumentException("private chat settings contain an unsupported key");
        }

        Map<String, String> result = new HashMap<>(processEnvironment);
        for (String key : values.stringPropertyNames()) {
            if (processEnvironment.containsKey(key)) {
                throw new IllegalArgumentException("private chat settings conflict with process environment");
            }
            String value = values.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("private chat settings contain an empty value");
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static Properties read(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        try (SeekableByteChannel channel = Files.newByteChannel(
                normalized, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             InputStream input = Channels.newInputStream(channel)) {
            if (channel.size() > MAX_BYTES) {
                throw new IllegalArgumentException("private chat settings file is too large");
            }
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("private chat settings file is too large");
            }
            Properties properties = new Properties() {
                @Override
                public synchronized Object put(Object key, Object value) {
                    if (containsKey(key)) {
                        throw new IllegalArgumentException("private chat settings contain a duplicate key");
                    }
                    return super.put(key, value);
                }
            };
            properties.load(new ByteArrayInputStream(bytes));
            return properties;
        } catch (IOException exception) {
            throw new IllegalArgumentException("private chat settings file is unavailable or invalid", exception);
        }
    }
}
