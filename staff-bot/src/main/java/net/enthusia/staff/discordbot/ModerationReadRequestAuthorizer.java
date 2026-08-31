package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/** Resolves and authorizes the immutable actor/guild/target binding for a private read. */
final class ModerationReadRequestAuthorizer {
    private static final Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{0,19}");

    private final long guildId;
    private final StaffModerationRuntime moderation;
    private final JDA jda;

    ModerationReadRequestAuthorizer(long guildId, StaffModerationRuntime moderation, JDA jda) {
        this.guildId = guildId;
        this.moderation = moderation;
        this.jda = jda;
    }

    ModerationReadContext authorize(ModerationReadApiModel.ReadRequest request) {
        requireGuildBinding(request);
        long actorId = snowflake(request.actorId(), "actor");
        ModerationReadTarget readTarget = ModerationReadTarget.parse(request.targetKey());
        Guild guild = requireGuild();
        Member actorMember = guild.retrieveMemberById(actorId).complete();
        Actor actor = moderation.actors().invoker(
                new DiscordUserId(Long.toUnsignedString(actorId)), actorMember.getEffectiveName());
        StaffModerationReadService.Target target = moderation.reads().discordTarget(
                new DiscordUserId(Long.toUnsignedString(readTarget.userId())));
        requireReadAuthority(actor, target);
        return new ModerationReadContext(actorId, actorMember, guild, target, readTarget);
    }

    private void requireGuildBinding(ModerationReadApiModel.ReadRequest request) {
        if (request == null || !Long.toUnsignedString(guildId).equals(request.guildId())) {
            throw new StaffReadAuthorization.DeniedException();
        }
    }

    private void requireReadAuthority(Actor actor, StaffModerationReadService.Target target) {
        Optional<Actor> targetStaff = moderation.actors().targetStaff(target);
        for (DiscordModerationOperation operation : List.of(
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                DiscordModerationOperation.VIEW_HISTORY,
                DiscordModerationOperation.VIEW_NOTES)) {
            moderation.authorization().require(actor, targetStaff, operation, ModerationPlatform.DISCORD);
        }
    }

    private Guild requireGuild() {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalStateException("staging guild unavailable");
        }
        return guild;
    }

    static long snowflake(String value, String label) {
        if (value == null || !SNOWFLAKE.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " ID is invalid");
        }
        return Long.parseUnsignedLong(value);
    }
}
