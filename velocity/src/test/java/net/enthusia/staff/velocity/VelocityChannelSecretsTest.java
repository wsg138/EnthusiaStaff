package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityChannelSecretsTest {
    private static final byte[] HUB_KEY = repeated((byte) 0x11);
    private static final byte[] SMP_KEY = repeated((byte) 0x22);
    private static final byte[] PROXY_KEY = repeated((byte) 0x33);

    @TempDir
    Path tempDir;

    @Test
    void completeEnvironmentConfigurationLoadsAllBackendKeys() throws IOException {
        VelocityConfiguration configuration = VelocityConfiguration.load(tempDir);
        Map<String, String> environment = Map.of(
                "ES_CHANNEL_HUB_SECRET", encoded(HUB_KEY),
                "ES_CHANNEL_SMP_SECRET", encoded(SMP_KEY),
                "ES_CHANNEL_VELOCITY_SECRET", encoded(PROXY_KEY),
                "ES_CHANNEL_TLS_KEYSTORE_PASSWORD", "store-password"
        );

        VelocityChannelSecrets.Loaded loaded = VelocityChannelSecrets.load(
                configuration,
                tempDir,
                environment::get
        );

        assertArrayEquals(HUB_KEY, loaded.backendKeys().get("HUB").getEncoded());
        assertArrayEquals(SMP_KEY, loaded.backendKeys().get("SMP").getEncoded());
        assertArrayEquals(PROXY_KEY, loaded.proxyKey().getEncoded());
        assertEquals("store-password", new String(loaded.tlsStorePassword()));
    }

    @Test
    void privateFileSupportsBloomWhenChannelEnvironmentIsUnavailable() throws IOException {
        VelocityConfiguration configuration = VelocityConfiguration.load(tempDir);
        Files.writeString(tempDir.resolve("channel.properties"), String.join("\n",
                "channel.proxy-secret=" + encoded(PROXY_KEY),
                "channel.tls-store-password=store-password",
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
        assertEquals("store-password", new String(loaded.tlsStorePassword()));
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
