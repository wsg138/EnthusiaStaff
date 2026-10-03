package net.enthusia.staff.paper.commandbridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class CanonicalCommandBridgeLinkVerifierTest {
    private static final UUID SUBJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final DiscordUserId DISCORD_ID = new DiscordUserId("123456789012345678");
    private static final DiscordUserId OTHER_DISCORD_ID = new DiscordUserId("223456789012345678");

    @Test
    void acceptsOnlyCurrentCanonicalMainOnTheSameLinkedSubject() {
        CanonicalCommandBridgeLinkVerifier verifier = verifier(subject(ACTOR_ID, SUBJECT_ID));
        assertTrue(verifier.isCurrentLink(new ModerationSubjectId(SUBJECT_ID), DISCORD_ID, ACTOR_ID));
        assertFalse(verifier.isCurrentLink(new ModerationSubjectId(OTHER_ID), DISCORD_ID, ACTOR_ID));
    }

    @Test
    void rejectsWhenMainAccountChangedOrDiscordLinkDisappeared() {
        CanonicalCommandBridgeLinkVerifier changedMain = verifier(subject(OTHER_ID, SUBJECT_ID));
        assertFalse(changedMain.isCurrentLink(new ModerationSubjectId(SUBJECT_ID), DISCORD_ID, ACTOR_ID));

        ModerationSubject minecraftOnly = new ModerationSubject(
                new ModerationSubjectId(SUBJECT_ID),
                Set.of(new MinecraftIdentityRef(ACTOR_ID)),
                Optional.of(new MainMinecraftAccount(ACTOR_ID, MainAccountSelectionSource.AUTOMATIC))
        );
        assertFalse(verifier(minecraftOnly).isCurrentLink(
                new ModerationSubjectId(SUBJECT_ID), DISCORD_ID, ACTOR_ID));
    }

    @Test
    void rejectsWhenOriginatingDiscordIdentityWasRemovedButAnotherRemains() {
        ModerationSubject relinked = new ModerationSubject(
                new ModerationSubjectId(SUBJECT_ID),
                Set.of(new DiscordIdentityRef(OTHER_DISCORD_ID), new MinecraftIdentityRef(ACTOR_ID)),
                Optional.of(new MainMinecraftAccount(ACTOR_ID, MainAccountSelectionSource.AUTOMATIC))
        );

        assertFalse(verifier(relinked).isCurrentLink(
                new ModerationSubjectId(SUBJECT_ID), DISCORD_ID, ACTOR_ID));
    }

    @Test
    void missingCurrentSubjectFailsClosed() {
        CanonicalCommandBridgeLinkVerifier verifier = new CanonicalCommandBridgeLinkVerifier(
                playerId -> Optional.empty()
        );
        assertFalse(verifier.isCurrentLink(new ModerationSubjectId(SUBJECT_ID), DISCORD_ID, ACTOR_ID));
    }

    private static CanonicalCommandBridgeLinkVerifier verifier(ModerationSubject subject) {
        return new CanonicalCommandBridgeLinkVerifier(
                playerId -> Optional.of(new DiscordModerationPersistenceStore.VersionedSubject(subject, 1L))
        );
    }

    private static ModerationSubject subject(UUID mainPlayerId, UUID subjectId) {
        return new ModerationSubject(
                new ModerationSubjectId(subjectId),
                Set.of(
                        new DiscordIdentityRef(DISCORD_ID),
                        new MinecraftIdentityRef(ACTOR_ID),
                        new MinecraftIdentityRef(OTHER_ID)
                ),
                Optional.of(new MainMinecraftAccount(mainPlayerId, MainAccountSelectionSource.AUTOMATIC))
        );
    }
}
