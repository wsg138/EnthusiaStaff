package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;
import org.junit.jupiter.api.Test;

final class PaperDiscordReviewIdentityTest {
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ANOTHER_ACTOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String DISCORD = String.join("", "123456", "789123", "456789");
    private static final String ANOTHER_DISCORD = String.join("", "987654", "321987", "654321");
    private static final Instant LINKED_AT = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    void codeVerifiedCurrentLinksMayAuthorizeReview() {
        assertTrue(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.DISCORD_CODE, ACTOR, DISCORD, true),
                ACTOR, DISCORD));
        assertTrue(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.MINECRAFT_CODE, ACTOR, DISCORD, true),
                ACTOR, DISCORD));
    }

    @Test
    void historicalAndDifferentDiscordOrMinecraftLinksNeverAuthorize() {
        assertFalse(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.DISCORD_CODE, ACTOR, DISCORD, false),
                ACTOR, DISCORD));
        assertFalse(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.DISCORD_CODE, ACTOR, DISCORD, true),
                ACTOR, ANOTHER_DISCORD));
        assertFalse(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.DISCORD_CODE, ANOTHER_ACTOR, DISCORD, true),
                ACTOR, DISCORD));
    }

    @Test
    void historicalImportsAndAdministrativeRecoveryAreNotOwnerVerification() {
        assertFalse(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.MIGRATED_DISCORDSRV, ACTOR, DISCORD, true),
                ACTOR, DISCORD));
        assertFalse(PaperIntegrationManager.verifiedReviewLink(
                link(DiscordMinecraftLinkSource.STAFF_RECOVERY, ACTOR, DISCORD, true),
                ACTOR, DISCORD));
    }

    private static VersionedLink link(
            DiscordMinecraftLinkSource source, UUID minecraftId, String discordId, boolean active
    ) {
        var identity = new DiscordMinecraftLink(
                new DiscordUserId(discordId), minecraftId, LINKED_AT,
                active ? Optional.empty() : Optional.of(LINKED_AT.plusSeconds(1)), source);
        return new VersionedLink(
                UUID.randomUUID(), new ModerationSubjectId(UUID.randomUUID()),
                identity, 0L, false);
    }
}
