package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.enthusia.staff.domain.investigation.EvasionAlert;

/** Sends a deliberately redacted D09 alert to the configured private Discord staff channel. */
final class JdaDiscordInvestigationAlertSink implements DiscordInvestigationAlertSink {
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
        String content = "<@&" + staffRoleId + "> Linked-alt investigation alert `" + alert.alertId()
                + "`. Review private moderation tools; no automatic punishment was applied.";
        try {
            channel.sendMessage(content)
                    .setAllowedMentions(List.of(Message.MentionType.ROLE))
                    .mentionRoles(List.of(staffRoleId))
                    .complete();
            return Delivery.success();
        } catch (RuntimeException exception) {
            return Delivery.retry("DISCORD_ALERT_SEND_FAILED");
        }
    }
}
