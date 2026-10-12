package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic JDA-observer admission tests; no Discord connection or network. */
class AiMirrorDurableReceiptIngressTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final long WRONG_CHANNEL = 1234567890123456777L;
    private static final int PENDING_CAPACITY = 64;
    private static final int SUBMISSIONS = 70;
    private static final int EXPECTED_DROPS = SUBMISSIONS - PENDING_CAPACITY;
    private static final int WAIT_SECONDS = 5;
    private static final String DESTINATION = "staging:synthetic-bridge:example.invalid:443";
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);

    @TempDir Path directory;

    @BeforeEach
    void privateDirectory() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void callbackNeverWaitsForBlockedFilesystemAndDropsWhenQueueFull()
            throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger persisted = new AtomicInteger();
        try (AiMirrorDurableReceiptIngress ingress = new AiMirrorDurableReceiptIngress(
                config(), publication -> {
                    entered.countDown();
                    try {
                        if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IOException("synthetic blocked worker exceeded bound");
                        }
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                        throw new IOException("synthetic worker interrupted");
                    }
                    persisted.incrementAndGet();
                })) {
            ingress.resume();
            ingress.published(receipt(CHANNEL));
            assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS));
            long beginning = System.nanoTime();
            for (int index = 0; index < SUBMISSIONS; index++) {
                ingress.published(receipt(CHANNEL));
            }
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beginning);
            assertTrue(durationMillis < 800L, "Discord callback was blocked by disk work");
            assertEquals(EXPECTED_DROPS, ingress.stats().capacityDrops());
            ingress.pause();
            ingress.published(receipt(CHANNEL));
            release.countDown();
            awaitPersisted(ingress, persisted);
            assertEquals(1, persisted.get());
            assertFalse(ingress.failed());
        } finally {
            release.countDown();
        }
    }

    @Test
    void completedPostSendReceiptPersistsEncryptedOutsideCallback()
            throws Exception {
        try (AiMirrorEncryptedOutbox store = encryptedStore();
                AiMirrorDurableReceiptIngress ingress =
                        AiMirrorDurableReceiptIngress.withStore(config(), store)) {
            ingress.resume();
            ingress.published(receipt(WRONG_CHANNEL));
            ingress.published(receipt(CHANNEL));
            waitForReceipt(ingress);
            assertEquals(1, ingress.stats().persisted());
            assertEquals(1, store.recover().size());
        }
        try (AiMirrorEncryptedOutbox reopened = encryptedStore()) {
            assertEquals(1, reopened.recover().size());
        }
    }

    @Test
    void failingJournalStopsFurtherAdmissionWithoutReposting() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (AiMirrorDurableReceiptIngress ingress = new AiMirrorDurableReceiptIngress(
                config(), publication -> {
                    calls.incrementAndGet();
                    attempted.countDown();
                    throw new IOException("synthetic disk read-only");
                })) {
            ingress.resume();
            ingress.published(receipt(CHANNEL));
            assertTrue(attempted.await(WAIT_SECONDS, TimeUnit.SECONDS));
            awaitFailure(ingress);
            ingress.resume();
            ingress.published(receipt(CHANNEL));
            assertEquals(1, calls.get());
            assertEquals(1, ingress.stats().storageFailures());
        }
    }

    private static void awaitPersisted(
            AiMirrorDurableReceiptIngress ingress, AtomicInteger processed)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (ingress.stats().persisted() == 0 && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertEquals(1, processed.get());
    }

    private static void awaitFailure(AiMirrorDurableReceiptIngress ingress)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!ingress.failed() && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertTrue(ingress.failed());
    }

    private static void waitForReceipt(AiMirrorDurableReceiptIngress ingress)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (ingress.stats().persisted() == 0 && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertEquals(1, ingress.stats().persisted());
    }

    private AiMirrorEncryptedOutbox encryptedStore() throws IOException {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        return new AiMirrorEncryptedOutbox(
                directory, key, DESTINATION, GUILD, Set.of(CHANNEL),
                "discord_general", FIXED);
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt(long channel) {
        return new DiscordChatPublication(
                GUILD, channel, "1234567890123456787",
                "rosechat:synthetic:nonblocking", "synthetic unformatted public chat");
    }
}
