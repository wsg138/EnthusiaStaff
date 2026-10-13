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

/**
 * The join event arrives after Discord admits a member. Remove accounts younger than
 * 21 days except explicitly allowlisted IDs, with authenticated private management.
 */
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
            // When no moderation listener is installed there is no updateCommands
            // owner; upsert just this command instead of replacing guild commands.
            guild.upsertCommand(command()).queue(
                    ignored -> LOGGER.log(System.Logger.Level.INFO, "account_age_bypass_command_registered"),
                    failure -> LOGGER.log(System.Logger.Level.ERROR,
                            "account_age_bypass_command_registration_failed type={0}",
                            failure.getClass().getSimpleName()));
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) {
            return;
        }
        Member admin = event.getMember();
        if (event.getGuild() == null || event.getGuild().getIdLong() != guildId
                || admin == null || !admin.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Only members with Manage Server permission can manage age exceptions.")
                    .setEphemeral(true).queue();
            return;
        }
        if (event.getOption("action") == null || event.getOption("user-id") == null) {
            event.reply("A valid action and exact user ID are required.").setEphemeral(true).queue();
            return;
        }
        String action = event.getOption("action").getAsString();
        String input = event.getOption("user-id").getAsString();
        final long target;
        try {
            target = DiscordAccountAgeBypassStore.parseId(input);
        } catch (IllegalArgumentException invalid) {
            event.reply("The Discord user ID must be 16-20 decimal digits.")
                    .setEphemeral(true).queue();
            return;
        }
        long actor = admin.getIdLong();
        event.deferReply(true).queue(hook -> {
            if (!workers.tryExecute(() -> {
                try {
                    String result = switch (action) {
                        case "add" -> bypass.add(target) ? "Bypass saved." : "ID was already allowed.";
                        case "remove" -> bypass.remove(target) ? "Bypass removed." : "ID was not allowed.";
                        case "check" -> bypass.contains(target) ? "ID is bypassed." : "ID is not bypassed.";
                        default -> "Unknown bypass action.";
                    };
                    if ("add".equals(action) || "remove".equals(action)) {
                        LOGGER.log(System.Logger.Level.INFO,
                                "discord_account_age_bypass_change action={0} actor={1} target={2}",
                                action, Long.toUnsignedString(actor), Long.toUnsignedString(target));
                    }
                    hook.editOriginal(result + " User ID: " + Long.toUnsignedString(target)).queue();
                } catch (IOException failure) {
                    LOGGER.log(System.Logger.Level.ERROR,
                            "discord_account_age_bypass_store_failed type={0}",
                            failure.getClass().getSimpleName());
                    hook.editOriginal("Could not save the bypass list; no change applied.").queue();
                }
            })) {
                hook.editOriginal("The staff bot is busy; retry the command.").queue();
            }
        });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getGuild().getIdLong() != guildId || event.getUser().isBot()
                || bypass.contains(event.getUser().getIdLong())
                || !tooNew(event.getUser().getTimeCreated().toInstant(), clock.instant())) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)
                || !guild.getSelfMember().canInteract(member)) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "discord_account_age_gate_permission_denied guild={0} user={1}",
                    guild.getId(), member.getId());
            return;
        }
        try {
            long memberId = member.getIdLong();
            Instant accountCreated = event.getUser().getTimeCreated().toInstant();
            member.kick().reason(AUDIT_REASON)
                    // Last-chance guard: an admin may add a bypass while REST
                    // is waiting in the JDA queue. Never kick after that grant.
                    .setCheck(() -> !bypass.contains(memberId)
                            && tooNew(accountCreated, clock.instant()))
                    .queue(
                            ignored -> LOGGER.log(System.Logger.Level.INFO,
                                    "discord_account_age_gate_kicked guild={0} user={1}",
                                    guild.getId(), member.getId()),
                            failure -> {
                                if (bypass.contains(memberId)
                                        || !tooNew(accountCreated, clock.instant())) {
                                    LOGGER.log(System.Logger.Level.INFO,
                                            "discord_account_age_gate_kick_suppressed guild={0} user={1}",
                                            guild.getId(), member.getId());
                                    return;
                                }
                                LOGGER.log(System.Logger.Level.ERROR,
                                        "discord_account_age_gate_kick_failed guild={0} user={1} error={2}",
                                        guild.getId(), member.getId(),
                                        failure.getClass().getSimpleName());
                            });
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "discord_account_age_gate_dispatch_failed guild={0} user={1} error={2}",
                    guild.getId(), member.getId(), failure.getClass().getSimpleName());
        }
    }
}
