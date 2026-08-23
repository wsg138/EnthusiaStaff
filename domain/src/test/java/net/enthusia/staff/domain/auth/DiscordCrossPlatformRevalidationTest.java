package net.enthusia.staff.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordCrossPlatformRevalidationTest {
    private final DiscordModerationAuthorizationService service = new DiscordModerationAuthorizationService(
            new DiscordAuthorizationLimits(
                    Duration.ofHours(2),
                    Duration.ofDays(7),
                    Duration.ofDays(30),
                    Duration.ofDays(7)
            )
    );

    @Test
    void minecraftMutationAlwaysRequiresAuthoritativePunishmentPolicyRevalidation() {
        DiscordAuthorizationRequest request = new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.MINECRAFT),
                List.of(new DiscordConsequenceIntent(
                        ModerationPlatform.MINECRAFT,
                        DiscordConsequenceType.WARNING,
                        SanctionLength.instant(),
                        false,
                        false
                ))
        );

        DiscordAuthorizationDecision decision = service.authorize(
                actor(StaffRank.MOD), Optional.empty(), request);

        assertTrue(decision.permitted());
        assertEquals(
                Set.of(DiscordEnforcementPrecondition.MINECRAFT_PUNISHMENT_POLICY_REVALIDATION),
                decision.requiredPreconditions()
        );
    }

    @Test
    void crossPlatformMutationKeepsMinecraftPolicyAndDiscordHierarchyAsSeparatePreconditions() {
        DiscordAuthorizationRequest request = new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                EnumSet.of(ModerationPlatform.DISCORD, ModerationPlatform.MINECRAFT),
                List.of(
                        new DiscordConsequenceIntent(
                                ModerationPlatform.DISCORD,
                                DiscordConsequenceType.MUTE,
                                SanctionLength.temporary(Duration.ofHours(1)),
                                false,
                                false
                        ),
                        new DiscordConsequenceIntent(
                                ModerationPlatform.MINECRAFT,
                                DiscordConsequenceType.WARNING,
                                SanctionLength.instant(),
                                false,
                                false
                        )
                )
        );

        DiscordAuthorizationDecision decision = service.authorize(
                actor(StaffRank.MOD), Optional.empty(), request);

        assertTrue(decision.permitted());
        assertEquals(
                EnumSet.of(
                        DiscordEnforcementPrecondition.DISCORD_ROLE_HIERARCHY,
                        DiscordEnforcementPrecondition.MINECRAFT_PUNISHMENT_POLICY_REVALIDATION
                ),
                decision.requiredPreconditions()
        );
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(UUID.randomUUID(), rank.name(), rank);
    }
}
