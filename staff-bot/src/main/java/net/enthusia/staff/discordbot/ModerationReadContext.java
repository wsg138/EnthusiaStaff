package net.enthusia.staff.discordbot;

import java.util.Optional;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

record ModerationReadContext(
        long actorId,
        Member actorMember,
        Guild guild,
        Optional<StaffModerationReadService.Target> target,
        ModerationReadTarget readTarget
) {
    ModerationReadContext {
        target = target == null ? Optional.empty() : target;
    }
}
