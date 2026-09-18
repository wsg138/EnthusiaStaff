package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.enthusia.staff.domain.investigation.EvasionAlert;

/** Sends the private D09 investigation snapshot and reauthorized quick actions to staff. */
final class JdaDiscordInvestigationAlertSink implements DiscordInvestigationAlertSink {
    private static final int MAX_INLINE_TEXT = 180;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(20);
    private static final String ROLE_UNAVAILABLE = "DISCORD_ALERT_STAFF_ROLE_UNAVAILABLE";
    private static final String CHANNEL_NOT_PRIVATE = "DISCORD_ALERT_CHANNEL_NOT_PRIVATE";
    private static final String STAFF_CANNOT_VIEW = "DISCORD_ALERT_STAFF_ROLE_CANNOT_VIEW";
    private static final String OTHER_ROLE_CAN_VIEW = "DISCORD_ALERT_CHANNEL_OTHER_ROLE_CAN_VIEW";
    private static final String MEMBER_OVERRIDE_CAN_VIEW = "DISCORD_ALERT_CHANNEL_MEMBER_OVERRIDE_CAN_VIEW";
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
            CompletableFuture<Message> request = channel.sendMessage("<@&" + staffRoleId + "> " + content(alert))
                    .setAllowedMentions(List.of(Message.MentionType.ROLE))
                    .mentionRoles(List.of(staffRoleId))
                    .addComponents(ActionRow.of(buttons(alert)))
                    .submit();
            return awaitSend(request, SEND_TIMEOUT);
        } catch (RuntimeException exception) {
            return Delivery.retry("DISCORD_ALERT_SEND_FAILED");
        }
    }

    static Delivery awaitSend(CompletableFuture<?> request, Duration timeout) {
        if (request == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Discord alert send wait is invalid");
        }
        try {
            request.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Delivery.success();
        } catch (TimeoutException exception) {
            request.cancel(true);
            return Delivery.retry("DISCORD_ALERT_SEND_TIMEOUT");
        } catch (ExecutionException exception) {
            return Delivery.retry("DISCORD_ALERT_SEND_FAILED");
        } catch (InterruptedException exception) {
            request.cancel(true);
            Thread.currentThread().interrupt();
            return Delivery.retry("DISCORD_ALERT_SEND_INTERRUPTED");
        }
    }

    private Optional<String> privacyError(TextChannel channel) {
        try {
            if (!channel.getJDA().getCacheFlags().contains(CacheFlag.MEMBER_OVERRIDES)) {
                return Optional.of(PRIVACY_UNAVAILABLE);
            }
            Role staffRole = channel.getGuild().getRoleById(staffRoleId);
            Optional<String> policyError = channelPolicyError(
                    staffRole != null,
                    channel.getGuild().getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL),
                    staffRole != null && staffRole.hasPermission(channel, Permission.VIEW_CHANNEL),
                    staffRole != null && hasUnexpectedRoleViewer(channel, staffRole),
                    hasUnexpectedMemberViewer(channel)
            );
            return policyError;
        } catch (RuntimeException exception) {
            return Optional.of(PRIVACY_UNAVAILABLE);
        }
    }

    private static boolean hasUnexpectedRoleViewer(TextChannel channel, Role staffRole) {
        Role publicRole = channel.getGuild().getPublicRole();
        long selfUserId = channel.getJDA().getSelfUser().getIdLong();
        return channel.getGuild().getRoles().stream()
                .filter(role -> !role.equals(publicRole) && !role.equals(staffRole))
                .filter(role -> !role.hasPermission(Permission.ADMINISTRATOR))
                .filter(role -> !isSelfBotRole(role, selfUserId))
                .anyMatch(role -> role.hasPermission(channel, Permission.VIEW_CHANNEL));
    }

    private static boolean hasUnexpectedMemberViewer(TextChannel channel) {
        long selfUserId = channel.getJDA().getSelfUser().getIdLong();
        return channel.getMemberPermissionOverrides().stream()
                .filter(override -> override.getIdLong() != selfUserId)
                .anyMatch(override -> override.getAllowed().contains(Permission.VIEW_CHANNEL));
    }

    private static boolean isSelfBotRole(Role role, long selfUserId) {
        return role.getTags().isBot() && role.getTags().getBotIdLong() == selfUserId;
    }

    static Optional<String> channelPolicyError(
            boolean staffRolePresent,
            boolean everyoneCanView,
            boolean staffCanView,
            boolean otherRoleCanView,
            boolean memberOverrideCanView
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
        if (otherRoleCanView) {
            return Optional.of(OTHER_ROLE_CAN_VIEW);
        }
        if (memberOverrideCanView) {
            return Optional.of(MEMBER_OVERRIDE_CAN_VIEW);
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
