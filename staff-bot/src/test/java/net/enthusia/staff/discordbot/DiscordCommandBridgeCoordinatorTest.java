package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import org.junit.jupiter.api.Test;

class DiscordCommandBridgeCoordinatorTest {
    private static final DiscordUserId DISCORD_ID = new DiscordUserId("123456789012345678");
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");

    @Test
    void resolvesCanonicalActorBeforeTransport() {
        AtomicInteger sends = new AtomicInteger();
        DiscordCommandBridgeCoordinator coordinator = coordinator(
                resolver(Optional.of(versionedSubject())),
                request -> {
                    sends.incrementAndGet();
                    assertEquals(REQUEST_ID, request.requestId());
                    assertEquals(new ModerationSubjectId(SUBJECT_ID), request.subjectId());
                    assertEquals(DISCORD_ID, request.discordUserId());
                    assertEquals(PLAYER_ID, request.actorPlayerId());
                    assertEquals(NOW, request.requestedAt());
                    return received(CommandBridgeResponse.withoutOutput(
                            CommandBridgeOutcome.SUCCESS,
                            "Command executed."
                    ));
                }
        );

        DiscordCommandBridgeCoordinator.Result result = coordinator.request(DISCORD_ID, "smp", "list");
        assertEquals(DiscordCommandBridgeCoordinator.Status.RECEIVED, result.status());
        assertEquals(1, sends.get());
    }

    @Test
    void unlinkedDiscordActorNeverReachesTransport() {
        AtomicInteger sends = new AtomicInteger();
        DiscordCommandBridgeCoordinator coordinator = coordinator(
                resolver(Optional.empty()),
                request -> {
                    sends.incrementAndGet();
                    return received(CommandBridgeResponse.withoutOutput(CommandBridgeOutcome.SUCCESS, "unexpected"));
                }
        );

        DiscordCommandBridgeCoordinator.Result result = coordinator.request(DISCORD_ID, "smp", "list");
        assertEquals(DiscordCommandBridgeCoordinator.Status.UNLINKED_ACTOR, result.status());
        assertEquals(0, sends.get());
        assertTrue(result.response().isEmpty());
    }

    @Test
    void ambiguousTransportResultStaysAmbiguous() {
        DiscordCommandBridgeCoordinator coordinator = coordinator(
                resolver(Optional.of(versionedSubject())),
                request -> HttpMinecraftCommandBridgeClient.Result.failed(
                        HttpMinecraftCommandBridgeClient.Status.AMBIGUOUS_FAILURE
                )
        );

        assertEquals(
                DiscordCommandBridgeCoordinator.Status.AMBIGUOUS_FAILURE,
                coordinator.request(DISCORD_ID, "smp", "list").status()
        );
    }

    private static DiscordCommandBridgeCoordinator coordinator(
            DiscordCommandActorResolver resolver,
            DiscordCommandBridgeCoordinator.Transport transport
    ) {
        return new DiscordCommandBridgeCoordinator(
                resolver,
                transport,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> REQUEST_ID
        );
    }

    private static DiscordCommandActorResolver resolver(
            Optional<DiscordModerationPersistenceStore.VersionedSubject> subject
    ) {
        return new DiscordCommandActorResolver(userId -> subject);
    }

    private static DiscordModerationPersistenceStore.VersionedSubject versionedSubject() {
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(SUBJECT_ID),
                Set.of(new DiscordIdentityRef(DISCORD_ID), new MinecraftIdentityRef(PLAYER_ID)),
                Optional.of(new MainMinecraftAccount(PLAYER_ID, MainAccountSelectionSource.AUTOMATIC))
        );
        return new DiscordModerationPersistenceStore.VersionedSubject(subject, 1L);
    }

    private static HttpMinecraftCommandBridgeClient.Result received(CommandBridgeResponse response) {
        return HttpMinecraftCommandBridgeClient.Result.received(response);
    }
}
