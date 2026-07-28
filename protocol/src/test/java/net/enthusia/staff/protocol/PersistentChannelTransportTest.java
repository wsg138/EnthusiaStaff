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
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentChannelTransportTest {
    private static final String BACKEND_ID = "SMP";
    private static final String PROXY_ID = "VELOCITY";

    @Test
    void authenticatesEncryptedFramesAndAcknowledgesBothDirections(@TempDir Path temporaryDirectory) throws Exception {
        ChannelFixture fixture = fixture(temporaryDirectory);
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch serverReceived = new CountDownLatch(1);
        CountDownLatch clientReceived = new CountDownLatch(1);

        try (PersistentChannelServer server = newServer(
                fixture,
                envelope -> {
                    serverReceived.countDown();
                    return true;
                }
        )) {
            server.start();
            try (PersistentChannelClient client = newClient(
                    fixture,
                    "localhost",
                    server.boundPort(),
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
        }
    }

    @Test
    void rejectsInvalidStoresAndAMismatchedCertificateHost(@TempDir Path temporaryDirectory) throws Exception {
        ChannelFixture fixture = fixture(temporaryDirectory);
        assertInvalidTlsStoresFailClosed(fixture.stores());
        CountDownLatch hostRejected = new CountDownLatch(1);

        try (PersistentChannelServer server = newServer(fixture, ignored -> true)) {
            server.start();
            try (PersistentChannelClient mismatchedHost = newClient(
                    fixture,
                    "127.0.0.1",
                    server.boundPort(),
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

    private static ChannelFixture fixture(Path temporaryDirectory) throws Exception {
        TlsStoreFixture.Stores stores = TlsStoreFixture.create(temporaryDirectory);
        return new ChannelFixture(
                key((byte) 11),
                key((byte) 29),
                serverTls(stores),
                clientTls(stores),
                stores
        );
    }

    private static PersistentChannelServer newServer(
            ChannelFixture fixture,
            ChannelMessageHandler handler
    ) throws java.net.UnknownHostException {
        return new PersistentChannelServer(
                new PersistentChannelServer.Configuration(
                        PROXY_ID,
                        InetAddress.getByName("127.0.0.1"),
                        0,
                        Map.of(BACKEND_ID, fixture.backendKey()),
                        fixture.proxyKey(),
                        fixture.serverTls(),
                        2
                ),
                Clock.systemUTC(),
                handler,
                ignored -> { }
        );
    }

    private static PersistentChannelClient newClient(
            ChannelFixture fixture,
            String host,
            int port,
            ChannelMessageHandler handler,
            Consumer<String> stateSink
    ) {
        return new PersistentChannelClient(
                new PersistentChannelClient.Configuration(
                        BACKEND_ID,
                        host,
                        port,
                        fixture.backendKey(),
                        PROXY_ID,
                        fixture.proxyKey(),
                        fixture.clientTls()
                ),
                Clock.systemUTC(),
                handler,
                stateSink
        );
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

    private record ChannelFixture(
            SecretKey backendKey,
            SecretKey proxyKey,
            SSLContext serverTls,
            SSLContext clientTls,
            TlsStoreFixture.Stores stores
    ) {
    }
}
