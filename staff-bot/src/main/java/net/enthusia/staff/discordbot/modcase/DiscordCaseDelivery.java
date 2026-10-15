package net.enthusia.staff.discordbot.modcase;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseDispatchWorker;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;

/** Dormant JDA sink. Never call from production without final scope and role acceptance. */
public final class DiscordCaseDelivery implements CaseDispatchWorker.Destination {
    private final Supplier<JDA> gateway;
    private final long guildId;
    private final long channelId;
    private final long staffRoleId;
    private final Predicate<CaseNotice> allowedScope;
    private final CaseLedger ledger;
    private final DiscordCaseControls controls;

    public DiscordCaseDelivery(Supplier<JDA> gateway, long guildId, long channelId, long staffRoleId,
                               Predicate<CaseNotice> allowedScope, CaseLedger ledger,
                               DiscordCaseControls controls) {
        if (guildId <= 0 || channelId <= 0 || staffRoleId <= 0) {
            throw new IllegalArgumentException("private staff destination required");
        }
        this.gateway = Objects.requireNonNull(gateway);
        this.guildId = guildId;
        this.channelId = channelId;
        this.staffRoleId = staffRoleId;
        this.allowedScope = Objects.requireNonNull(allowedScope);
        this.ledger = Objects.requireNonNull(ledger);
        this.controls = Objects.requireNonNull(controls);
    }

    @Override
    public CompletionStage<CaseDispatchWorker.Result> send(CaseNotice notice, CaseDelivery.Key key) {
        if (!matchesScope(notice, key)) {
            return rejected("SCOPE_NOT_AUTHORIZED");
        }
        TextChannel channel = verifiedChannel();
        if (channel == null) {
            return rejected("PRIVATE_CHANNEL_UNVERIFIED");
        }
        try {
            return publish(channel, notice, key);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(CaseDispatchWorker.Result.possiblySent("SEND_UNCONFIRMED"));
        }
    }

    private boolean matchesScope(CaseNotice notice, CaseDelivery.Key key) {
        return notice != null && key != null && key.caseId().equals(notice.caseId())
                && key.revision() == notice.revision() && key.origin() == notice.origin()
                && key.scope().equals(notice.scope()) && allowedScope.test(notice);
    }

    private TextChannel verifiedChannel() {
        try {
            JDA api = gateway.get();
            TextChannel channel = api == null ? null : api.getTextChannelById(channelId);
            if (channel == null || channel.getGuild().getIdLong() != guildId
                    || !privateToStaff(channel)) {
                return null;
            }
            return channel;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private CompletionStage<CaseDispatchWorker.Result> publish(
            TextChannel channel, CaseNotice notice, CaseDelivery.Key key) {
        String content = DiscordCaseCard.content(notice);
        String previous = ledger.previousReceipt(key).orElse(null);
        if (previous != null) {
            return channel.editMessageById(previous, content)
                    .setComponents(DiscordCaseCard.controls(notice, controls))
                    .submit().handle((message, error) -> result(message == null ? null : message.getId(), error));
        }
        return channel.sendMessage(content).setAllowedMentions(List.of())
                .addComponents(DiscordCaseCard.controls(notice, controls))
                .submit().handle((message, error) -> result(message == null ? null : message.getId(), error));
    }

    private boolean privateToStaff(TextChannel channel) {
        try {
            if (!channel.getJDA().getCacheFlags().contains(CacheFlag.MEMBER_OVERRIDES)) {
                return false;
            }
            var guild = channel.getGuild();
            Role role = guild.getRoleById(staffRoleId);
            return role != null && !guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL)
                    && role.hasPermission(channel, Permission.VIEW_CHANNEL)
                    && !otherRolesCanView(channel, role)
                    && !memberOverridesCanView(channel);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean otherRolesCanView(TextChannel channel, Role role) {
        var guild = channel.getGuild();
        long botId = channel.getJDA().getSelfUser().getIdLong();
        return guild.getRoles().stream().anyMatch(candidate ->
                !candidate.equals(guild.getPublicRole()) && !candidate.equals(role)
                && !(candidate.getTags().isBot() && candidate.getTags().getBotIdLong() == botId)
                && (candidate.hasPermission(Permission.ADMINISTRATOR)
                || candidate.hasPermission(channel, Permission.VIEW_CHANNEL)));
    }

    private static boolean memberOverridesCanView(TextChannel channel) {
        long botId = channel.getJDA().getSelfUser().getIdLong();
        return channel.getMemberPermissionOverrides().stream()
                .anyMatch(override -> override.getIdLong() != botId
                        && override.getAllowed().contains(Permission.VIEW_CHANNEL));
    }

    private static CaseDispatchWorker.Result result(String receipt, Throwable error) {
        return error == null && receipt != null
                ? CaseDispatchWorker.Result.confirmed(receipt)
                : CaseDispatchWorker.Result.possiblySent("SEND_UNCONFIRMED");
    }

    private static CompletionStage<CaseDispatchWorker.Result> rejected(String code) {
        return CompletableFuture.completedFuture(CaseDispatchWorker.Result.notSent(code));
    }
}
