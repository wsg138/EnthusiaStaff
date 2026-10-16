package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/** No real JDA gateway, moderation endpoint or environment is used. */
class AiMirrorDurableRecoverySessionTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String BINDING = "staging:synthetic-bridge:example.invalid:443";
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    private static final int TIMEOUT_SECONDS = 5;
    private static final int HTTP_OK = 200;
    @TempDir Path directory;

    @BeforeEach
    void privateDirectory() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void recoveryCannotStartUntilVerifiedChatLifecycleResume() throws Exception {
        FakeChat chat = new FakeChat();
        CountDownLatch posted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (AiMirrorEncryptedOutbox journal = openStore()) {
            journal.save(receipt());
            try (AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                    chat, journal, config(), request -> {
                        calls.incrementAndGet();
                        posted.countDown();
                        return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                    })) {
                assertEquals(0, calls.get());
                session.start();
                assertEquals(1, chat.started.get());
                assertFalse(posted.await(100, TimeUnit.MILLISECONDS));
                session.resume();
                assertTrue(posted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                awaitConfirmed(session);
                assertEquals(1, calls.get());
                assertEquals(1, session.recoveryStats().confirmed());
                assertEquals(1, chat.resumed.get());
            }
        }
        assertEquals(1, chat.closed.get());
    }

    @Test
    void identityCallbackBeforeChatStartupDefersRecoveryWithoutLosingReadiness()
            throws Exception {
        FakeChat chat = new FakeChat();
        CountDownLatch called = new CountDownLatch(1);
        try (AiMirrorEncryptedOutbox journal = openStore();
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                        chat, journal, config(), request -> {
                            called.countDown();
                            return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                        })) {
            journal.save(receipt());
            session.resume();
            assertFalse(called.await(150, TimeUnit.MILLISECONDS));
            session.start();
            assertTrue(called.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            awaitConfirmed(session);
            assertEquals(1, chat.started.get());
            assertEquals(1, chat.resumed.get());
        }
    }

    @Test
    void postSendDuringGatewayResumeIsDurablyCaptured() throws Exception {
        FakeChat chat = new FakeChat();
        try (AiMirrorEncryptedOutbox journal = openStore();
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                        chat, journal, config(), request ->
                        new AiMirrorRegistrationPublisher.PostResult(503, "storage_unavailable"))) {
            chat.onResume = () -> session.published(receipt());
            session.start();
            session.resume();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (session.ingressStats().persisted() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, session.ingressStats().persisted());
            assertEquals(1, session.ingressStats().eligiblePostSend());
            assertEquals(1, journal.recover().size());
            assertEquals(1, chat.resumed.get());
        }
    }

    @Test
    void pausedChatCannotAcceptNewPostSendReceipts() throws Exception {
        FakeChat chat = new FakeChat();
        try (AiMirrorEncryptedOutbox journal = openStore();
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                        chat, journal, config(), request ->
                        new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""))) {
            session.start();
            session.published(receipt());
            assertTrue(journal.recover().isEmpty());
            session.resume();
            session.pause();
            session.published(receipt());
            assertTrue(journal.recover().isEmpty());
            assertEquals(1, chat.paused.get());
            assertFalse(session.recoveryStats().confirmed() > 0);
        }
    }

    @Test
    void gatewayDisconnectSuspendsHttpUntilExplicitResume() throws Exception {
        FakeChat chat = new FakeChat();
        CountDownLatch posted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (AiMirrorEncryptedOutbox journal = openStore();
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                        chat, journal, config(), request -> {
                            calls.incrementAndGet();
                            posted.countDown();
                            return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                        })) {
            session.start();
            session.resume();
            session.pause();
            journal.save(receipt());
            assertFalse(posted.await(200, TimeUnit.MILLISECONDS));
            assertEquals(0, calls.get());
            session.resume();
            assertTrue(posted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            awaitConfirmed(session);
            assertEquals(1, calls.get());
            assertEquals(2, chat.resumed.get());
            assertEquals(1, chat.paused.get());
        }
    }

    @Test
    void noVolatileOrAdditionalDiscordPostsAreEverRequested() throws Exception {
        FakeChat chat = new FakeChat();
        try (AiMirrorEncryptedOutbox journal = openStore();
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                        chat, journal, config(), request ->
                        new AiMirrorRegistrationPublisher.PostResult(503, "storage_unavailable"))) {
            session.start();
            session.resume();
            session.published(receipt());
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (session.ingressStats().persisted() == 0 && System.nanoTime() < end) {
                Thread.sleep(10);
            }
            assertEquals(1, session.ingressStats().persisted());
            assertEquals(1, journal.recover().size());
            session.pause();
        }
        assertEquals(1, chat.closed.get());
    }

    private static void awaitConfirmed(AiMirrorDurableRecoverySession session)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (session.recoveryStats().confirmed() == 0 && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertEquals(1, session.recoveryStats().confirmed());
    }

    private AiMirrorEncryptedOutbox openStore() throws IOException {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        return new AiMirrorEncryptedOutbox(
                directory, key, BINDING, GUILD, Set.of(CHANNEL),
                "discord_general", FIXED);
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt() {
        return new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:lifecycle", "synthetic original text");
    }

    private static final class FakeChat implements StaffBotChatLifecycle {
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger resumed = new AtomicInteger();
        private final AtomicInteger paused = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private Runnable onResume = () -> { };
        @Override
        public void start() {
            started.incrementAndGet();
        }
        @Override
        public void resume() {
            resumed.incrementAndGet();
            onResume.run();
        }
        @Override
        public void pause() {
            paused.incrementAndGet();
        }
        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }
}
