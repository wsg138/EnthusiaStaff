package net.enthusia.staff.domain.auth;

import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.actor;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.discord;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.issue;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.minecraft;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordCrossPlatformRevalidationTest {
    @Test
    void minecraftMutationAlwaysRequiresAuthoritativePunishmentPolicyRevalidation() {
        DiscordAuthorizationDecision decision = service().authorize(
                actor(StaffRank.MOD),
                Optional.empty(),
                issue(minecraft(
                        DiscordConsequenceType.WARNING,
                        SanctionLength.instant(),
                        false,
                        false
                ))
        );
        assertTrue(decision.permitted());
        assertEquals(
                Set.of(DiscordEnforcementPrecondition.MINECRAFT_PUNISHMENT_POLICY_REVALIDATION),
                decision.requiredPreconditions()
        );
    }

    @Test
    void crossPlatformMutationKeepsIndependentConsequencesAndPreconditions() {
        DiscordAuthorizationRequest request = issue(
                discord(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(Duration.ofHours(1)),
                        false,
                        false
                ),
                minecraft(
                        DiscordConsequenceType.WARNING,
                        SanctionLength.instant(),
                        false,
                        false
                )
        );
        DiscordAuthorizationDecision decision = service().authorize(
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

    @Test
    void developerAndHelperCannotElevateThroughCrossPlatformSelection() {
        DiscordAuthorizationRequest request = issue(
                discord(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(Duration.ofHours(1)),
                        false,
                        false
                ),
                minecraft(
                        DiscordConsequenceType.WARNING,
                        SanctionLength.instant(),
                        false,
                        false
                )
        );
        assertEquals(
                DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED,
                service().authorize(actor(StaffRank.DEVELOPER), Optional.empty(), request).denial()
        );
        assertEquals(
                DiscordAuthorizationDenial.HELPER_CROSS_PLATFORM_FORBIDDEN,
                service().authorize(actor(StaffRank.HELPER), Optional.empty(), request).denial()
        );
    }
}
