package net.enthusia.staff.domain.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationSubjectTest {
    @Test
    void supportsDiscordOnlySubjects() {
        DiscordUserId discordId = new DiscordUserId("846729778400460871");
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                Set.of(new DiscordIdentityRef(discordId)),
                Optional.empty()
        );

        assertEquals(Set.of(discordId), subject.discordUserIds());
        assertTrue(subject.minecraftAccountIds().isEmpty());
        assertFalse(subject.linkedAcrossPlatforms());
    }

    @Test
    void supportsSeveralMinecraftIdentitiesWithOneDiscordIdentity() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MainMinecraftAccount main = new MainMinecraftAccount(first, MainAccountSelectionSource.AUTOMATIC);
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                Set.of(
                        new DiscordIdentityRef(new DiscordUserId("1062514905774829659")),
                        new MinecraftIdentityRef(first),
                        new MinecraftIdentityRef(second)
                ),
                Optional.of(main)
        );

        assertEquals(Set.of(first, second), subject.minecraftAccountIds());
        assertEquals(Optional.of(main), subject.mainMinecraftAccount());
        assertTrue(subject.linkedAcrossPlatforms());
    }

    @Test
    void preservesStaffOverrideAsPartOfEffectiveMainIdentity() {
        UUID playerId = UUID.randomUUID();
        MainMinecraftAccount main = new MainMinecraftAccount(playerId, MainAccountSelectionSource.STAFF_OVERRIDE);
        ModerationSubject subject = new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                Set.of(new MinecraftIdentityRef(playerId)),
                Optional.of(main)
        );

        assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, subject.mainMinecraftAccount().orElseThrow().source());
    }

    @Test
    void rejectsMainAccountOutsideSubject() {
        assertThrows(IllegalArgumentException.class, () -> new ModerationSubject(
                new ModerationSubjectId(UUID.randomUUID()),
                Set.of(new DiscordIdentityRef(new DiscordUserId("846729778400460871"))),
                Optional.of(new MainMinecraftAccount(UUID.randomUUID(), MainAccountSelectionSource.AUTOMATIC))
        ));
    }

    @Test
    void snowflakesNormalizeWithoutSignedLongAssumptions() {
        assertEquals("18446744073709551615", new DiscordUserId("18446744073709551615").value());
        assertEquals("123", new DiscordGuildId("000123").value());
        assertThrows(IllegalArgumentException.class, () -> new DiscordUserId("18446744073709551616"));
    }
}
