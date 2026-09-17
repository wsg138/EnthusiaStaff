package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.enthusia.staff.domain.investigation.EvasionAlert;

/** Sends the private D09 investigation snapshot and reauthorized quick actions to staff. */
final class JdaDiscordInvestigationAlertSink implements DiscordInvestigationAlertSink {
    private static final int MAX_INLINE_TEXT = 180;
    private static final String ROLE_UNAVAILABLE = "DISCORD_ALERT_STAFF_ROLE_UNAVAILABLE";
    private static final String CHANNEL_NOT_PRIVATE = "DISCORD_ALERT_CHANNEL_NOT_PRIVATE";
    private static final String STAFF_CANNOT_VIEW = "DISCORD_ALERT_STAFF_ROLE_CANNOT_VIEW";
    private static final String PRIVACY_UNAVAILABLE = "DISCORD_ALERT_CHANNEL_PRIVACY_UNAVAILABLE";

    private final long guildId;
    private final String channelId;
    private final String staffRoleId;
    private final AtomicReference<JDA> jda = new AtomicReference<>();

    JdaDiscordInvestigationAlertSink(long guildId, String channelId, String staffRoleId) {
        if (guildId <= 0 || channelId == null || channelId.isBlank() || staffRoleId == null || staffRoleId.isBlank()) {
            throw new IllegalArgumentException("Discord investigation alert destination is invalid");
        }
        this.guildId = guildId;
        this.channelId = channelId;
        this.staffRoleId = staffRoleId;
    }

    void bind(JDA api) {
        if (api == null) {
            throw new IllegalArgumentException("JDA must be present");
        }
        jda.set(api);
    }

    void unbind() {
        jda.set(null);
    }

    @Override
    public Delivery deliver(EvasionAlert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("alert must be present");
        }
        JDA api = jda.get();
        if (api == null) {
            return Delivery.retry("DISCORD_GATEWAY_UNAVAILABLE");
        }
        TextChannel channel = api.getTextChannelById(channelId);
        if (channel == null || channel.getGuild().getIdLong() != guildId) {
            return Delivery.retry("DISCORD_ALERT_CHANNEL_UNAVAILABLE");
        }
        Optional<String> privacyError = privacyError(channel);
        if (privacyError.isPresent()) {
            return Delivery.retry(privacyError.orElseThrow());
        }
        try {
            channel.sendMessage("<@&" + staffRoleId + "> " + content(alert))
                    .setAllowedMentions(List.of(Message.MentionType.ROLE))
                    .mentionRoles(List.of(staffRoleId))
                    .addComponents(ActionRow.of(buttons(alert)))
                    .complete();
            return Delivery.success();
        } catch (RuntimeException exception) {
            return Delivery.retry("DISCORD_ALERT_SEND_FAILED");
        }
    }

    private Optional<String> privacyError(TextChannel channel) {
        try {
            Role staffRole = channel.getGuild().getRoleById(staffRoleId);
            if (staffRole == null) {
                return channelPolicyError(false, false, false);
            }
            return channelPolicyError(
                    true,
                    channel.getGuild().getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL),
                    staffRole.hasPermission(channel, Permission.VIEW_CHANNEL)
            );
        } catch (RuntimeException exception) {
            return Optional.of(PRIVACY_UNAVAILABLE);
        }
    }

    static Optional<String> channelPolicyError(
            boolean staffRolePresent,
            boolean everyoneCanView,
            boolean staffCanView
    ) {
        if (!staffRolePresent) {
            return Optional.of(ROLE_UNAVAILABLE);
        }
        if (everyoneCanView) {
            return Optional.of(CHANNEL_NOT_PRIVATE);
        }
        if (!staffCanView) {
            return Optional.of(STAFF_CANNOT_VIEW);
        }
        return Optional.empty();
    }

    static String content(EvasionAlert alert) {
        String player = alert.triggeringMinecraftUsername().map(JdaDiscordInvestigationAlertSink::safe)
                .map(name -> name + " (`" + alert.triggeringMinecraftPlayerId() + "`)")
                .orElse("`" + alert.triggeringMinecraftPlayerId() + "`");
        String expiry = alert.punishmentExpiresAt().map(JdaDiscordInvestigationAlertSink::discordTime)
                .orElse("permanent");
        return "**Linked-alt investigation alert** `" + alert.alertId() + "`\n"
                + "Punished Discord: `" + alert.targetDiscordUserId().value() + "`\n"
                + "Linked Minecraft: " + player + "\n"
                + "Active punishment: **" + alert.punishmentType() + "** / " + alert.punishmentState()
                + " / " + expiry + " — " + safe(alert.punishmentSummary()) + "\n"
                + "Trigger: linked Minecraft account observed online on `" + safe(alert.currentServer()) + "` at "
                + discordTime(alert.triggeredAt()) + ".\n"
                + "No automatic punishment was applied; staff must make the evasion decision.";
    }

    private static List<Button> buttons(EvasionAlert alert) {
        return List.of(
                Button.secondary(DiscordInvestigationAlertControls.linked(alert), "Linked"),
                Button.secondary(DiscordInvestigationAlertControls.history(alert), "History"),
                Button.primary(DiscordInvestigationAlertControls.moderate(alert), "Moderate"),
                Button.success(DiscordInvestigationAlertControls.resolve(alert), "Resolve")
        );
    }

    private static String discordTime(Instant value) {
        return "<t:" + value.getEpochSecond() + ":R>";
    }

    private static String safe(String raw) {
        String value = raw.replace('\r', ' ').replace('\n', ' ').replace('`', '\'').replace("@", "＠").trim();
        return value.length() <= MAX_INLINE_TEXT ? value : value.substring(0, MAX_INLINE_TEXT - 1) + "…";
    }
}
