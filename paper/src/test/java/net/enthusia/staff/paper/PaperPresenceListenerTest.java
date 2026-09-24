package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import org.junit.jupiter.api.Test;

class PaperPresenceListenerTest {
    private static final Instant NOW = Instant.parse("2026-09-23T15:00:00Z");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final int FIRST_ATTEMPT = 1;
    private static final Logger LOGGER = Logger.getLogger(PaperPresenceListenerTest.class.getName());

    @Test
    void disconnectIsPersistedWithPaperBackendAndCapturedTime() {
        AtomicReference<Disconnect> recorded = new AtomicReference<>();
        PaperPresenceListener listener = listener(directory(recorded), Runnable::run, ignored -> {
            throw new AssertionError("retry not expected");
        });

        listener.recordDisconnected(PLAYER_ID);

        assertEquals(new Disconnect(PLAYER_ID, "smp-1", NOW), recorded.get());
    }

    @Test
    void rejectedWorkerSubmissionRetriesCapturedDisconnect() {
        AtomicReference<Disconnect> recorded = new AtomicReference<>();
        AtomicReference<Runnable> retry = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        PaperPresenceListener listener = listener(directory(recorded), operation -> {
            if (attempts.incrementAndGet() == FIRST_ATTEMPT) {
                throw new RejectedExecutionException("full");
            }
            operation.run();
        }, retry::set);

        listener.recordDisconnected(PLAYER_ID);

        assertNull(recorded.get());
        assertNotNull(retry.get());
        retry.get().run();
        assertEquals(2, attempts.get());
        assertEquals(new Disconnect(PLAYER_ID, "smp-1", NOW), recorded.get());
    }

    @Test
    void repeatedQueueRejectionStopsAfterBoundedAttempts() {
        List<Runnable> retries = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        PaperPresenceListener listener = listener(directory(new AtomicReference<>()), ignored -> {
            attempts.incrementAndGet();
            throw new RejectedExecutionException("full");
        }, retries::add);

        listener.recordDisconnected(PLAYER_ID);
        while (!retries.isEmpty()) {
            retries.removeFirst().run();
        }

        assertEquals(3, attempts.get());
        assertEquals(0, retries.size());
    }

    private static PaperPresenceListener listener(
            PlayerDirectory directory,
            java.util.function.Consumer<Runnable> submitter,
            java.util.function.Consumer<Runnable> retryScheduler
    ) {
        return new PaperPresenceListener(
                Clock.fixed(NOW, ZoneOffset.UTC), "smp-1", () -> directory,
                submitter, retryScheduler, LOGGER
        );
    }

    private static PlayerDirectory directory(AtomicReference<Disconnect> recorded) {
        return new PlayerDirectory() {
            @Override
            public Optional<PlayerIdentity> find(String uuidOrUsername) {
                return Optional.empty();
            }

            @Override
            public List<PlayerIdentity> search(String prefix, int limit) {
                return List.of();
            }

            @Override
            public Optional<PlayerPresence> presence(UUID playerId) {
                return Optional.empty();
            }

            @Override
            public void recordSeen(
                    UUID playerId, String username, PlayerPlatform platform, String serverId, Instant seenAt
            ) {
            }

            @Override
            public void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt) {
                recorded.set(new Disconnect(playerId, serverId, disconnectedAt));
            }
        };
    }

    private record Disconnect(UUID playerId, String serverId, Instant disconnectedAt) {
    }
}
