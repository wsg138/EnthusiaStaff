package net.enthusia.staff.persistence;

import java.util.List;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;

/** Pure conflict policy used while the target identity row is transactionally locked. */
final class DiscordPunishmentConflictPolicy {
    private DiscordPunishmentConflictPolicy() {
    }

    static boolean conflicts(DiscordPunishment requested, List<StoredPunishment> active) {
        return active.stream().map(StoredPunishment::punishment).anyMatch(existing -> conflicts(requested, existing));
    }

    private static boolean conflicts(DiscordPunishment requested, DiscordPunishment existing) {
        DiscordConsequenceType type = requested.intent().type();
        return switch (type) {
            case WARNING, KICK -> false;
            case MUTE, BAN -> true;
            case CHANNEL_RESTRICTION -> sameRestriction(requested, existing);
        };
    }

    private static boolean sameRestriction(DiscordPunishment first, DiscordPunishment second) {
        DiscordRestrictionTarget firstTarget = first.intent().restriction().orElseThrow();
        DiscordRestrictionTarget secondTarget = second.intent().restriction().orElseThrow();
        return firstTarget.kind() == secondTarget.kind()
                && firstTarget.snowflake().equals(secondTarget.snowflake());
    }
}
