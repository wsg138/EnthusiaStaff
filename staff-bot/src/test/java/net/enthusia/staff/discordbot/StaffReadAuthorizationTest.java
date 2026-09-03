package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import org.junit.jupiter.api.Test;

class StaffReadAuthorizationTest {
    private final StaffReadAuthorization authorization = new StaffReadAuthorization();

    @Test
    void helperCanUseReadOperations() {
        Actor helper = new Actor(UUID.randomUUID(), "helper", StaffRank.HELPER);

        assertDoesNotThrow(() -> authorization.require(
                helper,
                Optional.empty(),
                DiscordModerationOperation.VIEW_HISTORY,
                ModerationPlatform.DISCORD
        ));
        assertDoesNotThrow(() -> authorization.require(
                helper,
                Optional.empty(),
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                ModerationPlatform.MINECRAFT
        ));
    }

    @Test
    void systemCannotUseInteractiveReads() {
        Actor system = new Actor(UUID.randomUUID(), "system", StaffRank.SYSTEM);

        assertThrows(StaffReadAuthorization.DeniedException.class, () -> authorization.require(
                system,
                Optional.empty(),
                DiscordModerationOperation.VIEW_NOTES,
                ModerationPlatform.DISCORD
        ));
    }
}
