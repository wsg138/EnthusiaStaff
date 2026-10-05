package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import org.junit.jupiter.api.Test;

class DiscordCommandActorResolverTest {
    private static final DiscordUserId DISCORD_USER = new DiscordUserId("123456789012345678");
    private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void resolvesCanonicalLinkedMainAccount() {
        DiscordCommandActorResolver resolver = new DiscordCommandActorResolver(
                user -> Optional.of(versioned(linkedSubject())));

        DiscordCommandActorResolver.ResolvedActor actor = resolver.resolve(DISCORD_USER).orElseThrow();
        assertEquals(new ModerationSubjectId(SUBJECT_ID), actor.subjectId());
        assertEquals(PLAYER_ID, actor.actorPlayerId());
    }

    @Test
    void unlinkedDiscordActorCannotResolve() {
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(SUBJECT_ID),
                Set.of(new DiscordIdentityRef(DISCORD_USER)),
                Optional.empty()
        );
        DiscordCommandActorResolver resolver = new DiscordCommandActorResolver(
                user -> Optional.of(versioned(subject)));

        assertTrue(resolver.resolve(DISCORD_USER).isEmpty());
        assertTrue(new DiscordCommandActorResolver(user -> Optional.empty()).resolve(DISCORD_USER).isEmpty());
    }

    private static ModerationSubject linkedSubject() {
        return new ModerationSubject(
                new ModerationSubjectId(SUBJECT_ID),
                Set.of(new DiscordIdentityRef(DISCORD_USER), new MinecraftIdentityRef(PLAYER_ID)),
                Optional.of(new MainMinecraftAccount(PLAYER_ID, MainAccountSelectionSource.AUTOMATIC))
        );
    }

    private static DiscordModerationPersistenceStore.VersionedSubject versioned(ModerationSubject subject) {
        return new DiscordModerationPersistenceStore.VersionedSubject(subject, 1L);
    }
}
