package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentChannelTransportTest {
    private static final String BACKEND_ID = "SMP";
    private static final String PROXY_ID = "VELOCITY";

    @Test
    void authenticatesEncryptedFramesAndRejectsAMismatchedHost(@TempDir Path temporaryDirectory) throws Exception {
        SecretKey backendKey = key((byte) 11);
        SecretKey proxyKey = key((byte) 29);
        TlsStoreFixture.Stores stores = TlsStoreFixture.create(temporaryDirectory);
        assertInvalidTlsStoresFailClosed(stores);
        SSLContext serverTls = serverTls(stores);
        SSLContext clientTls = clientTls(stores);
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch serverReceived = new CountDownLatch(1);
        CountDownLatch clientReceived = new CountDownLatch(1);

        try (PersistentChannelServer server = new PersistentChannelServer(
                new PersistentChannelServer.Configuration(
                        PROXY_ID,
                        InetAddress.getByName("127.0.0.1"),
                        0,
                        Map.of(BACKEND_ID, backendKey),
                        proxyKey,
                        serverTls,
                        2
                ),
                Clock.systemUTC(),
                envelope -> {
                    serverReceived.countDown();
                    return true;
                },
                ignored -> { }
        )) {
            server.start();
            try (PersistentChannelClient client = new PersistentChannelClient(
                    new PersistentChannelClient.Configuration(
                            BACKEND_ID,
                            "localhost",
                            server.boundPort(),
                            backendKey,
                            PROXY_ID,
                            proxyKey,
                            clientTls
                    ),
                    Clock.systemUTC(),
                    envelope -> {
                        clientReceived.countDown();
                        return true;
                    },
                    state -> {
                        if ("CONNECTED".equals(state)) {
                            connected.countDown();
                        }
                    }
            )) {
                client.start();
                assertTrue(connected.await(5, TimeUnit.SECONDS));
                assertTrue(client.send(UUID.randomUUID(), "HEALTH", "{}", Duration.ofSeconds(3)).get());
                assertTrue(serverReceived.await(3, TimeUnit.SECONDS));

                PersistentChannelServer.DeliveryStatus result = server.send(
                        BACKEND_ID, UUID.randomUUID(), "PUNISHMENT_CREATED", "{}", Duration.ofSeconds(3)
                ).get();
                assertEquals(PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED, result);
                assertTrue(clientReceived.await(3, TimeUnit.SECONDS));
            }

            CountDownLatch hostRejected = new CountDownLatch(1);
            try (PersistentChannelClient mismatchedHost = new PersistentChannelClient(
                    new PersistentChannelClient.Configuration(
                            BACKEND_ID,
                            "127.0.0.1",
                            server.boundPort(),
                            backendKey,
                            PROXY_ID,
                            proxyKey,
                            clientTls
                    ),
                    Clock.systemUTC(),
                    ignored -> true,
                    state -> {
                        if ("CONNECTION_FAILED".equals(state)) {
                            hostRejected.countDown();
                        }
                    }
            )) {
                mismatchedHost.start();
                assertTrue(hostRejected.await(5, TimeUnit.SECONDS));
                assertFalse(mismatchedHost.connected());
            }
        }
    }

    private static void assertInvalidTlsStoresFailClosed(TlsStoreFixture.Stores stores) {
        char[] password = TlsStoreFixture.password();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> TlsContextLoader.server(stores.trustStore(), password)
            );
        } finally {
            Arrays.fill(password, '\0');
        }

        char[] incorrectPassword = "not-the-fixture-passphrase".toCharArray();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> TlsContextLoader.client(stores.trustStore(), incorrectPassword)
            );
        } finally {
            Arrays.fill(incorrectPassword, '\0');
        }
    }

    private static SSLContext serverTls(TlsStoreFixture.Stores stores) {
        char[] password = TlsStoreFixture.password();
        try {
            return TlsContextLoader.server(stores.keyStore(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static SSLContext clientTls(TlsStoreFixture.Stores stores) {
        char[] password = TlsStoreFixture.password();
        try {
            return TlsContextLoader.client(stores.trustStore(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static SecretKey key(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return SecretKeyMaterial.hmacSha256FromBase64(Base64.getEncoder().encodeToString(bytes));
    }
}
