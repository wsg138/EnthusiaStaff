package net.enthusia.staff.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

record ModerationReadContext(
        long actorId,
        Member actorMember,
        Guild guild,
        StaffModerationReadService.Target target,
        ModerationReadTarget readTarget
) {
}
