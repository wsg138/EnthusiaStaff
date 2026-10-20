package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * Unregistered JDA transport for future explicitly authorized private reports.
 * Construction does not register a listener or send a message.
 */
final class JdaPrivateReportNotificationTransport
        implements PrivateReportNotificationDispatcher.DestinationVerifier,
                   PrivateReportNotificationDispatcher.Transport {
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_MESSAGE_LENGTH = 1_900;
    private final JDA jda;
    private final String guildId;
    private final String channelId;
    private final String staffRoleId;
    private final boolean ownerApproved;

    JdaPrivateReportNotificationTransport(
            JDA jda, String guildId, String channelId, String staffRoleId,
            boolean ownerApproved
    ) {
        this.jda = Objects.requireNonNull(jda, "jda");
        this.guildId = requireSnowflake(guildId);
        this.channelId = requireSnowflake(channelId);
        this.staffRoleId = requireSnowflake(staffRoleId);
        this.ownerApproved = ownerApproved;
    }

    @Override
    public PrivateReportNotificationTemplate.DestinationProof verify() {
        if (!ownerApproved) {
            return denied();
        }
        try {
            return verifyChannel(jda.getTextChannelById(channelId));
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    /** Verify the exact channel instance that will receive the message. */
    private PrivateReportNotificationTemplate.DestinationProof verifyChannel(TextChannel channel) {
        if (!ownerApproved) {
            return denied();
        }
        try {
            if (channel == null || !channel.getGuild().getId().equals(guildId)
                    || !jda.getCacheFlags().contains(CacheFlag.MEMBER_OVERRIDES)) {
                return denied();
            }
            Role staffRole = channel.getGuild().getRoleById(staffRoleId);
            boolean present = staffRole != null;
            boolean otherRoleViewer = present && hasExtraViewer(channel, staffRole);
            boolean memberOverrideViewer = hasMemberOverrideViewer(channel);
            return new PrivateReportNotificationTemplate.DestinationProof(
                    true, guildId, channel.getGuild().getId(), true, present,
                    channel.getGuild().getPublicRole().hasPermission(
                            channel, Permission.VIEW_CHANNEL),
                    present && staffRole.hasPermission(channel, Permission.VIEW_CHANNEL),
                    otherRoleViewer, memberOverrideViewer);
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    @Override
    public PrivateReportNotificationDispatcher.Outcome send(String message) {
        if (!ownerApproved || message == null || message.isBlank()
                || message.length() > MAX_MESSAGE_LENGTH) {
            return PrivateReportNotificationDispatcher.Outcome.UNCERTAIN;
        }
        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (!verifyChannel(channel).eligible()) {
                return PrivateReportNotificationDispatcher.Outcome.UNCERTAIN;
            }
            return sendConfirmed(channel, message);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return PrivateReportNotificationDispatcher.Outcome.UNCERTAIN;
        } catch (ExecutionException | TimeoutException | RuntimeException ambiguous) {
            return PrivateReportNotificationDispatcher.Outcome.UNCERTAIN;
        }
    }

    private static PrivateReportNotificationDispatcher.Outcome sendConfirmed(
            TextChannel channel, String message
    ) throws InterruptedException, ExecutionException, TimeoutException {
        // User-created report text must never ping users, roles or @everyone.
        Message delivered = channel.sendMessage(message)
                .setAllowedMentions(List.of())
                .submit().get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        return delivered == null
                ? PrivateReportNotificationDispatcher.Outcome.UNCERTAIN
                : PrivateReportNotificationDispatcher.Outcome.CONFIRMED;
    }

    private PrivateReportNotificationTemplate.DestinationProof denied() {
        return new PrivateReportNotificationTemplate.DestinationProof(
                false, guildId, "", false, false, true, false, true, true);
    }

    private boolean hasExtraViewer(TextChannel channel, Role staffRole) {
        Role publicRole = channel.getGuild().getPublicRole();
        long selfUser = jda.getSelfUser().getIdLong();
        return channel.getGuild().getRoles().stream().anyMatch(role ->
                JdaDiscordInvestigationAlertSink.unexpectedRoleCanView(
                        role.equals(publicRole),
                        role.equals(staffRole),
                        role.getTags().isBot() && role.getTags().getBotIdLong() == selfUser,
                        role.hasPermission(Permission.ADMINISTRATOR),
                        role.hasPermission(channel, Permission.VIEW_CHANNEL)));
    }

    private boolean hasMemberOverrideViewer(TextChannel channel) {
        long selfUser = jda.getSelfUser().getIdLong();
        return channel.getMemberPermissionOverrides().stream()
                .filter(override -> override.getIdLong() != selfUser)
                .anyMatch(override -> override.getAllowed().contains(Permission.VIEW_CHANNEL));
    }

    private static String requireSnowflake(String value) {
        if (value == null || !value.matches("[1-9][0-9]{5,19}")) {
            throw new IllegalArgumentException("Discord destination ID must be an unsigned snowflake");
        }
        return value;
    }
}
