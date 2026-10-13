package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Checks Discord user creation time at join, not Minecraft registration or guild join age.
 * Events fire *after* Discord admits the member; the bot promptly kicks underage accounts.
 */
final class DiscordAccountAgeGateListener extends ListenerAdapter {
    private static final System.Logger LOGGER =
            System.getLogger(DiscordAccountAgeGateListener.class.getName());
    static final Duration MINIMUM_ACCOUNT_AGE = Duration.ofDays(21);
    private static final String AUDIT_REASON = "Enthusia account-age gate: Discord account under 21 days old";
    private final long guildId;
    private final Clock clock;

    DiscordAccountAgeGateListener(long guildId, Clock clock) {
        if (guildId <= 0) {
            throw new IllegalArgumentException("guild ID must be positive");
        }
        this.guildId = guildId;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    static boolean tooNew(Instant accountCreatedAt, Instant now) {
        Objects.requireNonNull(accountCreatedAt, "accountCreatedAt");
        Objects.requireNonNull(now, "now");
        return accountCreatedAt.isAfter(now.minus(MINIMUM_ACCOUNT_AGE));
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getGuild().getIdLong() != guildId || event.getUser().isBot()
                || !tooNew(event.getUser().getTimeCreated().toInstant(), clock.instant())) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        // Never claim to enforce when the required permission/hierarchy is absent.
        if (!guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)
                || !guild.getSelfMember().canInteract(member)) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "discord_account_age_gate_permission_denied guild={0} user={1}",
                    guild.getId(), member.getId());
            return;
        }
        try {
            member.kick().reason(AUDIT_REASON).queue(
                    ignored -> LOGGER.log(System.Logger.Level.INFO,
                            "discord_account_age_gate_kicked guild={0} user={1}",
                            guild.getId(), member.getId()),
                    failure -> LOGGER.log(System.Logger.Level.ERROR,
                            "discord_account_age_gate_kick_failed guild={0} user={1} error={2}",
                            guild.getId(), member.getId(), failure.getClass().getSimpleName()));
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "discord_account_age_gate_dispatch_failed guild={0} user={1} error={2}",
                    guild.getId(), member.getId(), failure.getClass().getSimpleName());
        }
    }
}
