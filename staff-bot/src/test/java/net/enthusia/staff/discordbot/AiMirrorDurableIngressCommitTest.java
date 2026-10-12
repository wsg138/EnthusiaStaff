package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses only isolated synthetic receipts; no network, JDA or live config. */
class AiMirrorDurableIngressCommitTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    @TempDir Path directory;

    @BeforeEach
    void privateDirectory() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void callbackReceivesAuthenticatedFsyncedReceiptOffCallerThread() throws Exception {
        CountDownLatch observed = new CountDownLatch(1);
        AtomicReference<AiMirrorEncryptedOutbox.Saved> delivered = new AtomicReference<>();
        AtomicReference<String> callbackThread = new AtomicReference<>();
        String callerThread = Thread.currentThread().getName();

        try (AiMirrorEncryptedOutbox store = openStore();
                AiMirrorDurableReceiptIngress ingress =
                        AiMirrorDurableReceiptIngress.withStore(config(), store, saved -> {
                            callbackThread.set(Thread.currentThread().getName());
                            delivered.set(saved);
                            observed.countDown();
                        })) {
            ingress.resume();
            ingress.published(receipt(CHANNEL));
            assertTrue(observed.await(5, TimeUnit.SECONDS));
            var records = store.recover();
            assertEquals(1, records.size());
            assertEquals(records.getFirst(), delivered.get());
            assertFalse(callerThread.equals(callbackThread.get()));
        }
    }

    @Test
    void invalidChannelNeverTriggersPostCommitCallback() throws Exception {
        CountDownLatch observed = new CountDownLatch(1);
        try (AiMirrorEncryptedOutbox store = openStore();
                AiMirrorDurableReceiptIngress ingress =
                        AiMirrorDurableReceiptIngress.withStore(config(), store,
                                saved -> observed.countDown())) {
            ingress.resume();
            ingress.published(receipt(CHANNEL + 100));
            assertFalse(observed.await(150, TimeUnit.MILLISECONDS));
            assertTrue(store.recover().isEmpty());
        }
    }

    private AiMirrorEncryptedOutbox openStore() throws IOException {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        return new AiMirrorEncryptedOutbox(directory, key,
                "staging:synthetic-bridge:example.invalid:443", GUILD,
                Set.of(CHANNEL), "discord_general", FIXED);
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt(long channel) {
        return new DiscordChatPublication(GUILD, channel, "1234567890123456787",
                "rosechat:synthetic:commit", "synthetic original chat");
    }
}
