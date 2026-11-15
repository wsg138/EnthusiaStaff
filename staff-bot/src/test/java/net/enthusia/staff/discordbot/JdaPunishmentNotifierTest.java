package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class JdaPunishmentNotifierTest {
    private static final Instant NOW = Instant.parse("2026-10-05T05:00:00Z");
    private static final String TARGET_USER_ID = "123";
    private static final String GUILD_ID = "456";

    @Test
    void temporaryDiscordPunishmentIsReadableAndUsesDiscordExpiryTimestamp() {
        JdaPunishmentNotifier notifier = new JdaPunishmentNotifier(configuration());
        DiscordPunishment punishment = DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId(TARGET_USER_ID),
                new DiscordGuildId(GUILD_ID),
                new Actor(UUID.randomUUID(), "Staff", StaffRank.ADMIN),
                new DiscordPunishmentIntent(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(Duration.ofDays(1)),
                        false,
                        false,
                        Optional.empty(),
                        "custom",
                        "Referencing a controversial historical figure\n"
                                + "Discord message reference: 1556501761831735297\n"
                                + "External evidence reference: private-ticket",
                        0,
                        true
                ),
                NOW,
                "issue"
        );

        String message = notifier.appliedMessage(punishment);

        assertTrue(message.startsWith("# Punishment Alert"));
        assertTrue(message.contains("You have been `muted` on the Enthusia SMP Discord for `1 day`."));
        assertTrue(message.contains("**Reason:** custom"));
        assertTrue(message.contains("**Staff explanation:** Referencing a controversial historical figure"));
        assertFalse(message.contains("Discord message reference:"));
        assertFalse(message.contains("External evidence reference:"));
        assertTrue(message.contains("<t:" + NOW.plus(Duration.ofDays(1)).getEpochSecond() + ":F>"));
        assertTrue(message.contains("<t:" + NOW.plus(Duration.ofDays(1)).getEpochSecond() + ":R>"));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_CHANNEL));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_SITE));
    }

    @Test
    void warningDoesNotPretendToBePermanent() {
        JdaPunishmentNotifier notifier = new JdaPunishmentNotifier(configuration());
        DiscordPunishment punishment = DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId(TARGET_USER_ID),
                new DiscordGuildId(GUILD_ID),
                new Actor(UUID.randomUUID(), "Staff", StaffRank.ADMIN),
                new DiscordPunishmentIntent(
                        DiscordConsequenceType.WARNING,
                        SanctionLength.instant(),
                        false,
                        false,
                        Optional.empty(),
                        "Chat behavior",
                        "",
                        0,
                        true
                ),
                NOW,
                "issue"
        );

        String message = notifier.appliedMessage(punishment);

        assertTrue(message.contains("You have been `warned` on the Enthusia SMP Discord."));
        assertFalse(message.contains("**Expires:**"));
    }

    @Test
    void selfPreviewUsesTheProductionMessageShapeWithoutRealPunishmentData() {
        String warning = JdaPunishmentNotifier.previewMessage(DiscordConsequenceType.WARNING, NOW);
        String mute = JdaPunishmentNotifier.previewMessage(DiscordConsequenceType.MUTE, NOW);
        String ban = JdaPunishmentNotifier.previewMessage(DiscordConsequenceType.BAN, NOW);

        assertTrue(warning.contains("You have been `warned`"));
        assertFalse(warning.contains("**Expires:**"));
        assertTrue(mute.contains("You have been `muted` on the Enthusia SMP Discord for `1 hour`."));
        assertTrue(mute.contains("<t:" + NOW.plus(Duration.ofHours(1)).getEpochSecond() + ":R>"));
        assertTrue(ban.contains("You have been `banned` on the Enthusia SMP Discord for `7 days`."));
        assertTrue(ban.contains("<t:" + NOW.plus(Duration.ofDays(7)).getEpochSecond() + ":R>"));
        for (String message : java.util.List.of(warning, mute, ban)) {
            assertTrue(message.contains("**Reason:** Notification test"));
            assertTrue(message.contains("No punishment was created or applied."));
            assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_SITE));
        }
    }

    @Test
    void proposedPunishmentPreviewMatchesTheExactTargetNotificationTemplate() {
        for (DiscordConsequenceType type : java.util.List.of(
                DiscordConsequenceType.WARNING, DiscordConsequenceType.MUTE,
                DiscordConsequenceType.KICK, DiscordConsequenceType.BAN)) {
            SanctionLength length = switch (type) {
                case WARNING, KICK -> SanctionLength.instant();
                default -> SanctionLength.temporary(Duration.ofHours(3));
            };
            DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                    type, length, false, false, Optional.empty(),
                    "Real staff reason", "Public explanation", 0, true);
            DiscordPunishment punishment = DiscordPunishment.pending(
                    UUID.randomUUID(), new ModerationSubjectId(UUID.randomUUID()),
                    new DiscordUserId(TARGET_USER_ID), new DiscordGuildId(GUILD_ID),
                    new Actor(UUID.randomUUID(), "Staff", StaffRank.ADMIN),
                    intent, NOW, "issue");

            assertEquals(new JdaPunishmentNotifier(configuration()).appliedMessage(punishment),
                    JdaPunishmentNotifier.previewMessage(intent, NOW));
        }
    }

    @Test
    void previewPresentationUsesBrandingFieldsAndAccentColor() {
        String message = JdaPunishmentNotifier.previewMessage(DiscordConsequenceType.MUTE, NOW);
        String icon = "https://cdn.discordapp.com/icons/1410303324745371709/example.png";
        var embed = PunishmentNotificationDiscordPresentation.embed(
                message, PunishmentNotificationDiscordPresentation.MUTE_COLOR, icon);

        assertEquals("Punishment Alert", embed.getTitle());
        assertEquals(PunishmentNotificationDiscordPresentation.MUTE_COLOR, embed.getColorRaw());
        assertTrue(embed.getDescription().contains("You have been"));
        assertTrue(embed.getFields().stream().anyMatch(field -> "Reason".equals(field.getName())));
        assertTrue(embed.getFields().stream().anyMatch(field -> "Expires".equals(field.getName())));
        assertNotNull(embed.getThumbnail());
        assertEquals(icon, embed.getThumbnail().getUrl());
        assertEquals(PunishmentNotificationDiscordPresentation.FOOTER, embed.getFooter().getText());
        assertFalse(embed.getDescription().contains(JdaPunishmentNotifier.APPEAL_SITE));
        assertFalse(embed.getDescription().contains(JdaPunishmentNotifier.APPEAL_CHANNEL));
    }

    @Test
    void actionColorsAreDistinctForWarningMuteAndBan() {
        assertEquals(PunishmentNotificationDiscordPresentation.WARNING_COLOR,
                PunishmentNotificationDiscordPresentation.color(DiscordConsequenceType.WARNING));
        assertEquals(PunishmentNotificationDiscordPresentation.MUTE_COLOR,
                PunishmentNotificationDiscordPresentation.color(DiscordConsequenceType.MUTE));
        assertEquals(PunishmentNotificationDiscordPresentation.BAN_COLOR,
                PunishmentNotificationDiscordPresentation.color(DiscordConsequenceType.BAN));
        assertEquals(PunishmentNotificationDiscordPresentation.BAN_COLOR,
                PunishmentNotificationDiscordPresentation.color(SanctionType.BAN));
    }
    @Test
    void linkedMinecraftWarningAndMuteMessagesUseMinecraftContext() {
        String warning = JdaPunishmentNotifier.minecraftWarningOrMuteMessage(
                new JdaPunishmentNotifier.MinecraftWarningOrMuteNotification(
                        TARGET_USER_ID,
                        "ExamplePlayer",
                        "Chat spam",
                        NOW,
                        SanctionType.WARNING,
                        Optional.empty()
                )
        );
        String mute = JdaPunishmentNotifier.minecraftWarningOrMuteMessage(
                new JdaPunishmentNotifier.MinecraftWarningOrMuteNotification(
                        TARGET_USER_ID,
                        "ExamplePlayer",
                        "Repeated chat spam",
                        NOW,
                        SanctionType.MUTE,
                        Optional.of(NOW.plus(Duration.ofHours(6)))
                )
        );

        assertTrue(warning.contains("Minecraft account `ExamplePlayer`"));
        assertTrue(warning.contains("has been `warned` on the Enthusia SMP."));
        assertTrue(warning.contains("**Reason:** Chat spam"));
        assertFalse(warning.contains("**Expires:**"));

        assertTrue(mute.contains("Minecraft account `ExamplePlayer`"));
        assertTrue(mute.contains("has been `muted` on the Enthusia SMP until the time shown below."));
        assertTrue(mute.contains("**Reason:** Repeated chat spam"));
        assertTrue(mute.contains("<t:" + NOW.plus(Duration.ofHours(6)).getEpochSecond() + ":R>"));
        assertTrue(mute.contains(JdaPunishmentNotifier.APPEAL_SITE));
    }

    @Test
    void linkedMinecraftBanMessageUsesPlayerNameReasonAndAppealRoutes() {
        String message = JdaPunishmentNotifier.minecraftBanMessage(
                new JdaPunishmentNotifier.MinecraftBanNotification(
                        TARGET_USER_ID,
                        "ExamplePlayer",
                        "Cheating",
                        NOW,
                        Optional.of(NOW.plus(Duration.ofDays(30)))
                )
        );

        assertTrue(message.startsWith("# Punishment Alert"));
        assertTrue(message.contains("Minecraft account `ExamplePlayer`"));
        assertTrue(message.contains("**Reason:** Cheating"));
        assertTrue(message.contains("<t:" + NOW.plus(Duration.ofDays(30)).getEpochSecond() + ":R>"));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_CHANNEL));
        assertTrue(message.contains(JdaPunishmentNotifier.APPEAL_SITE));
    }

    private static DiscordPunishmentConfiguration configuration() {
        return new DiscordPunishmentConfiguration(
                new DiscordAuthorizationLimits(
                        Duration.ofHours(1),
                        Duration.ofDays(7),
                        Duration.ofDays(30),
                        Duration.ofDays(30)
                ),
                TARGET_USER_ID,
                Set.of(GUILD_ID),
                "Support message",
                Duration.ofMinutes(1),
                Duration.ofSeconds(1)
        );
    }
}
