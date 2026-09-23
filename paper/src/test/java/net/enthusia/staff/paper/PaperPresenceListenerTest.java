package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
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

    @Test
    void disconnectIsPersistedWithPaperBackendAndCapturedTime() {
        AtomicReference<Disconnect> recorded = new AtomicReference<>();
        PlayerDirectory directory = directory(recorded);
        PaperPresenceListener listener = new PaperPresenceListener(
                Clock.fixed(NOW, ZoneOffset.UTC),
                "smp-1",
                () -> directory,
                Runnable::run,
                Logger.getLogger(PaperPresenceListenerTest.class.getName())
        );

        listener.recordDisconnected(PLAYER_ID);

        assertEquals(new Disconnect(PLAYER_ID, "smp-1", NOW), recorded.get());
    }

    @Test
    void rejectedWorkerSubmissionDoesNotEscapeTheListener() {
        PaperPresenceListener listener = new PaperPresenceListener(
                Clock.fixed(NOW, ZoneOffset.UTC),
                "smp-1",
                () -> directory(new AtomicReference<>()),
                ignored -> {
                    throw new RejectedExecutionException("full");
                },
                Logger.getLogger(PaperPresenceListenerTest.class.getName())
        );

        assertDoesNotThrow(() -> listener.recordDisconnected(PLAYER_ID));
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
                    UUID playerId,
                    String username,
                    PlayerPlatform platform,
                    String serverId,
                    Instant seenAt
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
