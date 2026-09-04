package net.enthusia.staff.domain.auth;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;

final class DiscordAuthorizationTestFixtures {
    static final Duration HELPER_MUTE = Duration.ofHours(2);
    static final Duration MOD_MUTE = Duration.ofDays(7);
    static final Duration MOD_BAN = Duration.ofDays(30);
    static final Duration MOD_RESTRICTION = Duration.ofDays(7);

    private DiscordAuthorizationTestFixtures() {
    }

    static DiscordModerationAuthorizationService service() {
        return new DiscordModerationAuthorizationService(new DiscordAuthorizationLimits(
                HELPER_MUTE, MOD_MUTE, MOD_BAN, MOD_RESTRICTION));
    }

    static DiscordAuthorizationRequest issue(DiscordConsequenceIntent... consequences) {
        EnumSet<ModerationPlatform> platforms = EnumSet.noneOf(ModerationPlatform.class);
        for (DiscordConsequenceIntent consequence : consequences) {
            platforms.add(consequence.platform());
        }
        return new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                platforms,
                List.of(consequences)
        );
    }

    static DiscordAuthorizationRequest operation(
            DiscordModerationOperation operation,
            ModerationPlatform... platforms
    ) {
        return new DiscordAuthorizationRequest(operation, Set.of(platforms), List.of());
    }

    static DiscordConsequenceIntent discord(
            DiscordConsequenceType type,
            SanctionLength length,
            boolean customDuration,
            boolean customConsequence
    ) {
        return new DiscordConsequenceIntent(
                ModerationPlatform.DISCORD, type, length, customDuration, customConsequence);
    }

    static DiscordConsequenceIntent minecraft(
            DiscordConsequenceType type,
            SanctionLength length,
            boolean customDuration,
            boolean customConsequence
    ) {
        return new DiscordConsequenceIntent(
                ModerationPlatform.MINECRAFT, type, length, customDuration, customConsequence);
    }

    static Actor actor(StaffRank rank) {
        return new Actor(UUID.randomUUID(), rank.name(), rank);
    }
}
