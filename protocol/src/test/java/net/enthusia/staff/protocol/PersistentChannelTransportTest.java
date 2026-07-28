package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import net.enthusia.staff.common.security.SecretKeyMaterial;
import org.junit.jupiter.api.Test;

class PersistentChannelTransportTest {
    @Test
    void authenticatesBothDirectionsAndAcknowledgesFrames() throws Exception {
        SecretKey backendKey = key((byte) 11);
        SecretKey proxyKey = key((byte) 29);
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch serverReceived = new CountDownLatch(1);
        CountDownLatch clientReceived = new CountDownLatch(1);

        try (PersistentChannelServer server = new PersistentChannelServer(
                "VELOCITY",
                InetAddress.getLoopbackAddress(),
                0,
                Map.of("SMP", backendKey),
                proxyKey,
                Clock.systemUTC(),
                2,
                envelope -> {
                    serverReceived.countDown();
                    return true;
                },
                ignored -> { }
        )) {
            server.start();
            try (PersistentChannelClient client = new PersistentChannelClient(
                    "SMP",
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    server.boundPort(),
                    backendKey,
                    "VELOCITY",
                    proxyKey,
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
                        "SMP", UUID.randomUUID(), "PUNISHMENT_CREATED", "{}", Duration.ofSeconds(3)
                ).get();
                assertEquals(PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED, result);
                assertTrue(clientReceived.await(3, TimeUnit.SECONDS));
            }
        }
    }

    private static SecretKey key(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return SecretKeyMaterial.hmacSha256FromBase64(Base64.getEncoder().encodeToString(bytes));
    }
}
