package net.enthusia.staff.domain.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscordMinecraftLinkPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Test
    void oneDiscordMayOwnSeveralCurrentMinecraftLinks() {
        DiscordUserId discord = new DiscordUserId("846729778400460871");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<DiscordMinecraftLink> links = List.of(
                active(discord, first),
                active(discord, second)
        );

        DiscordMinecraftLinkPolicy.validateCurrentOwnership(links);
        assertEquals(Set.of(first, second), DiscordMinecraftLinkPolicy.currentMinecraftAccounts(discord, links));
    }

    @Test
    void oneMinecraftAccountCannotHaveTwoCurrentDiscordOwners() {
        UUID playerId = UUID.randomUUID();
        List<DiscordMinecraftLink> links = List.of(
                active(new DiscordUserId("846729778400460871"), playerId),
                active(new DiscordUserId("1062514905774829659"), playerId)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordMinecraftLinkPolicy.validateCurrentOwnership(links)
        );
    }

    @Test
    void historicalOwnerDoesNotBlockAReplacementCurrentOwner() {
        UUID playerId = UUID.randomUUID();
        DiscordUserId historical = new DiscordUserId("846729778400460871");
        DiscordUserId current = new DiscordUserId("1062514905774829659");
        List<DiscordMinecraftLink> links = List.of(
                new DiscordMinecraftLink(
                        historical,
                        playerId,
                        NOW.minusSeconds(3600),
                        Optional.of(NOW.minusSeconds(1800)),
                        DiscordMinecraftLinkSource.MIGRATED_DISCORDSRV
                ),
                active(current, playerId)
        );

        assertEquals(Optional.of(current), DiscordMinecraftLinkPolicy.currentDiscordOwner(playerId, links));
    }

    private static DiscordMinecraftLink active(DiscordUserId discordUserId, UUID playerId) {
        return new DiscordMinecraftLink(
                discordUserId,
                playerId,
                NOW,
                Optional.empty(),
                DiscordMinecraftLinkSource.DISCORD_CODE
        );
    }
}
