package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import org.junit.jupiter.api.Test;

class LinkedStaffActorResolverTest {
    private static final DiscordUserId DISCORD = new DiscordUserId("123456789");
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void discordIdentityAloneCannotGrantStaffAuthority() {
        LinkedStaffActorResolver resolver = new LinkedStaffActorResolver(
                ignored -> Optional.of(subject()),
                ignored -> Optional.empty()
        );

        assertThrows(
                LinkedStaffActorResolver.MissingStaffLinkException.class,
                () -> resolver.invoker(DISCORD, "discord-role-holder")
        );
    }

    @Test
    void exactCurrentLinkedRankBuildsActor() {
        LinkedStaffActorResolver resolver = new LinkedStaffActorResolver(
                ignored -> Optional.of(subject()),
                playerId -> playerId.equals(PLAYER) ? Optional.of(StaffRank.DEVELOPER) : Optional.empty()
        );

        Actor actor = resolver.invoker(DISCORD, "staff-user");

        assertEquals(PLAYER, actor.id());
        assertEquals(StaffRank.DEVELOPER, actor.rank());
        assertEquals("staff-user", actor.displayName());
    }

    private static VersionedSubject subject() {
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                Set.of(new MinecraftIdentityRef(PLAYER), new DiscordIdentityRef(DISCORD)),
                Optional.of(new MainMinecraftAccount(PLAYER, MainAccountSelectionSource.AUTOMATIC))
        );
        return new VersionedSubject(subject, 4L);
    }
}
