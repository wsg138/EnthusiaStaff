package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/** Checks newly joined human Discord accounts against a 21-day threshold and durable bypass list. */
final class DiscordAccountAgeGateListener extends ListenerAdapter {
    static final String COMMAND = "agebypass";
    private static final System.Logger LOGGER =
            System.getLogger(DiscordAccountAgeGateListener.class.getName());
    static final Duration MINIMUM_ACCOUNT_AGE = Duration.ofDays(21);
    private static final String AUDIT_REASON = "Enthusia account-age gate: Discord account under 21 days old";

    private final long guildId;
    private final Clock clock;
    private final DiscordAccountAgeBypassStore bypass;
    private final StaffBotWorkerPool workers;
    private final boolean registerStandaloneCommand;

    DiscordAccountAgeGateListener(long guildId, Clock clock, DiscordAccountAgeBypassStore bypass,
            StaffBotWorkerPool workers, boolean registerStandaloneCommand) {
        if (guildId <= 0) {
            throw new IllegalArgumentException("guild ID must be positive");
        }
        this.guildId = guildId;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bypass = Objects.requireNonNull(bypass, "bypass");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.registerStandaloneCommand = registerStandaloneCommand;
    }

    static boolean tooNew(Instant accountCreatedAt, Instant now) {
        Objects.requireNonNull(accountCreatedAt, "accountCreatedAt");
        Objects.requireNonNull(now, "now");
        return accountCreatedAt.isAfter(now.minus(MINIMUM_ACCOUNT_AGE));
    }

    /** Re-evaluate at dispatch so a newly granted bypass suppresses an already queued kick. */
    static boolean kickEligible(boolean isBot, boolean bypassed, Instant createdAt, Instant now) {
        return !isBot && !bypassed && tooNew(createdAt, now);
    }

    static CommandData command() {
        return Commands.slash(COMMAND, "Manage Discord account-age join exceptions")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Bypass action", true)
                                .addChoice("Allow ID", "add")
                                .addChoice("Remove ID", "remove")
                                .addChoice("Check ID", "check"),
                        new OptionData(OptionType.STRING, "user-id",
                                "Exact Discord user ID (works before someone joins)", true))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (!registerStandaloneCommand) {
            return;
        }
        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild != null) {
            // No moderation command-list owner: upsert rather than replace all guild commands.
            guild.upsertCommand(command()).queue(
                    ignored -> logInfo("account_age_bypass_command_registered"),
                    failure -> logError("account_age_bypass_command_registration_failed type={0}",
                            failure.getClass().getSimpleName()));
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) {
            return;
        }
        Member admin = event.getMember();
        if (!authorized(event.getGuild(), admin)) {
            replyPrivate(event, "Only members with Manage Server permission can manage age exceptions.");
            return;
        }
        if (event.getOption("action") == null || event.getOption("user-id") == null) {
            replyPrivate(event, "A valid action and exact user ID are required.");
            return;
        }
        String action = event.getOption("action").getAsString();
        final long target;
        try {
            target = DiscordAccountAgeBypassStore.parseId(event.getOption("user-id").getAsString());
        } catch (IllegalArgumentException invalid) {
            replyPrivate(event, "The Discord user ID must be 16-20 decimal digits.");
            return;
        }
        queueCommand(event, admin.getIdLong(), action, target);
    }

    private boolean authorized(Guild guild, Member admin) {
        return guild != null && guild.getIdLong() == guildId
                && admin != null && admin.hasPermission(Permission.MANAGE_SERVER);
    }

    private static void replyPrivate(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void queueCommand(SlashCommandInteractionEvent event, long actor, String action, long target) {
        event.deferReply(true).queue(hook -> {
            if (!workers.tryExecute(() -> executeCommand(hook, actor, action, target))) {
                hook.editOriginal("The staff bot is busy; retry the command.").queue();
            }
        });
    }

    private void executeCommand(net.dv8tion.jda.api.interactions.InteractionHook hook,
            long actor, String action, long target) {
        try {
            String result = applyAction(action, target);
            if (isMutation(action)) {
                logInfo("discord_account_age_bypass_change action={0} actor={1} target={2}",
                        action, Long.toUnsignedString(actor), Long.toUnsignedString(target));
            }
            hook.editOriginal(result + " User ID: " + Long.toUnsignedString(target)).queue();
        } catch (IOException failure) {
            logError("discord_account_age_bypass_store_failed type={0}",
                    failure.getClass().getSimpleName());
            hook.editOriginal("Could not save the bypass list; no change applied.").queue();
        }
    }

    private String applyAction(String action, long target) throws IOException {
        return switch (action) {
            case "add" -> bypass.add(target) ? "Bypass saved." : "ID was already allowed.";
            case "remove" -> bypass.remove(target) ? "Bypass removed." : "ID was not allowed.";
            case "check" -> bypass.contains(target) ? "ID is bypassed." : "ID is not bypassed.";
            default -> "Unknown bypass action.";
        };
    }

    private static boolean isMutation(String action) {
        return "add".equals(action) || "remove".equals(action);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (!requiresKick(event)) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!canKick(guild, member)) {
            logError("discord_account_age_gate_permission_denied guild={0} user={1}",
                    guild.getId(), member.getId());
            return;
        }
        queueKick(event, guild, member);
    }

    private boolean requiresKick(GuildMemberJoinEvent event) {
        return event.getGuild().getIdLong() == guildId
                && kickEligible(event.getUser().isBot(),
                        bypass.contains(event.getUser().getIdLong()),
                        event.getUser().getTimeCreated().toInstant(), clock.instant());
    }

    private static boolean canKick(Guild guild, Member member) {
        return guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)
                && guild.getSelfMember().canInteract(member);
    }

    private void queueKick(GuildMemberJoinEvent event, Guild guild, Member member) {
        try {
            long memberId = member.getIdLong();
            Instant createdAt = event.getUser().getTimeCreated().toInstant();
            member.kick().reason(AUDIT_REASON)
                    // Recheck immediately before sending: an admin may have just granted a bypass.
                    .setCheck(() -> kickEligible(false, bypass.contains(memberId),
                            createdAt, clock.instant()))
                    .queue(
                            ignored -> logInfo("discord_account_age_gate_kicked guild={0} user={1}",
                                    guild.getId(), member.getId()),
                            failure -> kickFailed(guild, member, createdAt, failure));
        } catch (RuntimeException failure) {
            logError("discord_account_age_gate_dispatch_failed guild={0} user={1} error={2}",
                    guild.getId(), member.getId(), failure.getClass().getSimpleName());
        }
    }

    private void kickFailed(Guild guild, Member member, Instant createdAt, Throwable failure) {
        if (!kickEligible(false, bypass.contains(member.getIdLong()),
                createdAt, clock.instant())) {
            logInfo("discord_account_age_gate_kick_suppressed guild={0} user={1}",
                    guild.getId(), member.getId());
            return;
        }
        logError("discord_account_age_gate_kick_failed guild={0} user={1} error={2}",
                guild.getId(), member.getId(), failure.getClass().getSimpleName());
    }

    private static void logInfo(String pattern, Object... parameters) {
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(System.Logger.Level.INFO, pattern, parameters);
        }
    }

    private static void logError(String pattern, Object... parameters) {
        if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
            LOGGER.log(System.Logger.Level.ERROR, pattern, parameters);
        }
    }
}
