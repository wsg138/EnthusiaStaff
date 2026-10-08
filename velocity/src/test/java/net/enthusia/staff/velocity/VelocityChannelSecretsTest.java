package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityChannelSecretsTest {
    private static final byte[] HUB_KEY = repeated((byte) 0x11);
    private static final byte[] SMP_KEY = repeated((byte) 0x22);
    private static final byte[] PROXY_KEY = repeated((byte) 0x33);
    private static final String STORE_PASSWORD = "store-password";

    @TempDir
    Path tempDir;

    @Test
    void completeEnvironmentConfigurationLoadsAllBackendKeys() throws IOException {
        VelocityConfiguration configuration = VelocityConfiguration.load(tempDir);
        Map<String, String> environment = Map.of(
                "ES_CHANNEL_HUB_SECRET", encoded(HUB_KEY),
                "ES_CHANNEL_SMP_SECRET", encoded(SMP_KEY),
                "ES_CHANNEL_VELOCITY_SECRET", encoded(PROXY_KEY),
                "ES_CHANNEL_TLS_KEYSTORE_PASSWORD", STORE_PASSWORD
        );

        VelocityChannelSecrets.Loaded loaded = VelocityChannelSecrets.load(
                configuration,
                tempDir,
                environment::get
        );

        assertArrayEquals(HUB_KEY, loaded.backendKeys().get("HUB").getEncoded());
        assertArrayEquals(SMP_KEY, loaded.backendKeys().get("SMP").getEncoded());
        assertArrayEquals(PROXY_KEY, loaded.proxyKey().getEncoded());
        assertEquals(STORE_PASSWORD, new String(loaded.tlsStorePassword()));
    }

    @Test
    void privateFileSupportsBloomWhenChannelEnvironmentIsUnavailable() throws IOException {
        VelocityConfiguration configuration = VelocityConfiguration.load(tempDir);
        Files.writeString(tempDir.resolve("channel.properties"), String.join("\n",
                "channel.proxy-secret=" + encoded(PROXY_KEY),
                "channel.tls-store-password=" + STORE_PASSWORD,
                "channel.backend.HUB.secret=" + encoded(HUB_KEY),
                "channel.backend.SMP.secret=" + encoded(SMP_KEY),
                ""), StandardCharsets.UTF_8);

        VelocityChannelSecrets.Loaded loaded = VelocityChannelSecrets.load(
                configuration,
                tempDir,
                ignored -> null
        );

        assertArrayEquals(HUB_KEY, loaded.backendKeys().get("HUB").getEncoded());
        assertArrayEquals(SMP_KEY, loaded.backendKeys().get("SMP").getEncoded());
        assertArrayEquals(PROXY_KEY, loaded.proxyKey().getEncoded());
        assertEquals(STORE_PASSWORD, new String(loaded.tlsStorePassword()));
    }

    @Test
    void bloomMigrationLoadsAllExistingPeersPlusAuthenticatedStaffBotFromPrivateFile() throws IOException {
        VelocityConfiguration.load(tempDir);
        Files.writeString(tempDir.resolve("config.properties"), String.join("\n",
                "",
                "channel.backend.TEST.secret-environment=ES_CHANNEL_TEST_SECRET",
                "channel.backend.TEMP.secret-environment=ES_CHANNEL_TEMP_SECRET",
                "channel.backend.STAFFBOT.secret-environment=ES_CHANNEL_STAFFBOT_SECRET",
                ""), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        VelocityConfiguration configuration = VelocityConfiguration.load(tempDir);
        byte[] staffbotKey = repeated((byte) 0x44);
        byte[] testKey = repeated((byte) 0x55);
        byte[] tempKey = repeated((byte) 0x66);
        Files.writeString(tempDir.resolve("channel.properties"), String.join("\n",
                "channel.proxy-secret=" + encoded(PROXY_KEY),
                "channel.tls-store-password=" + STORE_PASSWORD,
                "channel.backend.HUB.secret=" + encoded(HUB_KEY),
                "channel.backend.SMP.secret=" + encoded(SMP_KEY),
                "channel.backend.TEST.secret=" + encoded(testKey),
                "channel.backend.TEMP.secret=" + encoded(tempKey),
                "channel.backend.STAFFBOT.secret=" + encoded(staffbotKey),
                ""), StandardCharsets.UTF_8);

        VelocityChannelSecrets.Loaded loaded = VelocityChannelSecrets.load(
                configuration, tempDir, ignored -> null);

        assertEquals(Set.of("HUB", "SMP", "TEST", "TEMP", "STAFFBOT"),
                loaded.backendKeys().keySet());
        assertArrayEquals(HUB_KEY, loaded.backendKeys().get("HUB").getEncoded());
        assertArrayEquals(SMP_KEY, loaded.backendKeys().get("SMP").getEncoded());
        assertArrayEquals(testKey, loaded.backendKeys().get("TEST").getEncoded());
        assertArrayEquals(tempKey, loaded.backendKeys().get("TEMP").getEncoded());
        assertArrayEquals(staffbotKey, loaded.backendKeys().get("STAFFBOT").getEncoded());
        assertArrayEquals(PROXY_KEY, loaded.proxyKey().getEncoded());
        assertEquals(STORE_PASSWORD, new String(loaded.tlsStorePassword()));
    }

    @Test
    void addingStaffBotPeerWithoutMatchingPrivateKeyFailsClosed() throws IOException {
        VelocityConfiguration.load(tempDir);
        Files.writeString(tempDir.resolve("config.properties"),
                "\nchannel.backend.STAFFBOT.secret-environment=ES_CHANNEL_STAFFBOT_SECRET\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        VelocityConfiguration configuration = VelocityConfiguration.load(tempDir);
        Files.writeString(tempDir.resolve("channel.properties"), String.join("\n",
                "channel.proxy-secret=" + encoded(PROXY_KEY),
                "channel.tls-store-password=" + STORE_PASSWORD,
                "channel.backend.HUB.secret=" + encoded(HUB_KEY),
                "channel.backend.SMP.secret=" + encoded(SMP_KEY),
                ""), StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class,
                () -> VelocityChannelSecrets.load(configuration, tempDir, ignored -> null));
    }

    private static byte[] repeated(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private static String encoded(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
