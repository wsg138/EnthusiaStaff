package net.enthusia.staff.domain.auth;

import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.HELPER_MUTE;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.MOD_BAN;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.MOD_MUTE;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.MOD_RESTRICTION;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.actor;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.discord;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.issue;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.minecraft;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordConsequenceAuthorizationTest {
    @Test
    void helperIsLimitedToConfiguredWarningsAndShortTemporaryMutes() {
        DiscordModerationAuthorizationService service = service();
        Actor helper = actor(StaffRank.HELPER);
        assertAllowed(service, helper, issue(discord(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)));
        assertAllowed(service, helper, issue(discord(
                DiscordConsequenceType.MUTE, SanctionLength.temporary(HELPER_MUTE), false, false)));
        assertDenied(service, helper, issue(discord(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(HELPER_MUTE.plusSeconds(1)), false, false)),
                DiscordAuthorizationDenial.DURATION_EXCEEDS_LIMIT);
        assertDenied(service, helper, issue(discord(
                        DiscordConsequenceType.MUTE, SanctionLength.permanent(), false, false)),
                DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
        assertDenied(service, helper, issue(discord(
                        DiscordConsequenceType.KICK, SanctionLength.instant(), false, false)),
                DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
        assertDenied(service, helper, issue(discord(
                        DiscordConsequenceType.BAN, SanctionLength.temporary(Duration.ofHours(1)), false, false)),
                DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
        assertDenied(service, helper, issue(discord(
                        DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofHours(1)), true, false)),
                DiscordAuthorizationDenial.CUSTOM_DURATION_NOT_PERMITTED);
        assertDenied(service, helper, issue(minecraft(
                        DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)),
                DiscordAuthorizationDenial.HELPER_CROSS_PLATFORM_FORBIDDEN);
    }

    @Test
    void moderatorAndDeveloperShareDiscordAuthorityButNotMinecraftAuthority() {
        DiscordModerationAuthorizationService service = service();
        for (StaffRank rank : List.of(StaffRank.MOD, StaffRank.DEVELOPER)) {
            assertModeratorDiscordConsequences(service, actor(rank));
        }
        assertAllowed(service, actor(StaffRank.MOD), issue(minecraft(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)));
        assertDenied(service, actor(StaffRank.DEVELOPER), issue(minecraft(
                        DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)),
                DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
    }

    @Test
    void adminAndFounderReceivePermanentDiscordAuthority() {
        DiscordModerationAuthorizationService service = service();
        for (StaffRank rank : List.of(StaffRank.ADMIN, StaffRank.FOUNDER)) {
            Actor actor = actor(rank);
            assertAllowed(service, actor, issue(discord(
                    DiscordConsequenceType.MUTE, SanctionLength.permanent(), false, false)));
            assertAllowed(service, actor, issue(discord(
                    DiscordConsequenceType.BAN, SanctionLength.permanent(), false, true)));
            assertAllowed(service, actor, issue(discord(
                    DiscordConsequenceType.CHANNEL_RESTRICTION, SanctionLength.permanent(), false, true)));
        }
    }

    @Test
    void minecraftCustomAuthorityRemainsOwnedByExistingPolicy() {
        DiscordModerationAuthorizationService service = service();
        assertAllowed(service, actor(StaffRank.ADMIN), issue(minecraft(
                DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofDays(45)), true, false)));
        assertDenied(service, actor(StaffRank.ADMIN), issue(minecraft(
                        DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofDays(45)), false, true)),
                DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
        assertAllowed(service, actor(StaffRank.FOUNDER), issue(minecraft(
                DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofDays(45)), false, true)));
    }

    private static void assertModeratorDiscordConsequences(
            DiscordModerationAuthorizationService service,
            Actor actor
    ) {
        assertAllowed(service, actor, issue(discord(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)));
        assertAllowed(service, actor, issue(discord(
                DiscordConsequenceType.KICK, SanctionLength.instant(), false, false)));
        assertAllowed(service, actor, issue(discord(
                DiscordConsequenceType.MUTE, SanctionLength.temporary(MOD_MUTE), true, false)));
        assertAllowed(service, actor, issue(discord(
                DiscordConsequenceType.BAN, SanctionLength.temporary(MOD_BAN), true, false)));
        assertAllowed(service, actor, issue(discord(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(MOD_RESTRICTION), true, false)));
        assertDenied(service, actor, issue(discord(
                        DiscordConsequenceType.BAN, SanctionLength.permanent(), false, false)),
                DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
        assertDenied(service, actor, issue(discord(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(MOD_MUTE.plusSeconds(1)), false, false)),
                DiscordAuthorizationDenial.DURATION_EXCEEDS_LIMIT);
        assertDenied(service, actor, issue(discord(
                        DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(Duration.ofHours(1)), false, true)),
                DiscordAuthorizationDenial.CUSTOM_CONSEQUENCE_REQUIRES_ADMIN);
    }

    private static void assertAllowed(
            DiscordModerationAuthorizationService service,
            Actor actor,
            DiscordAuthorizationRequest request
    ) {
        DiscordAuthorizationDecision decision = service.authorize(actor, Optional.empty(), request);
        assertTrue(decision.permitted(), () -> actor.rank() + " denied: " + decision.denial());
    }

    private static void assertDenied(
            DiscordModerationAuthorizationService service,
            Actor actor,
            DiscordAuthorizationRequest request,
            DiscordAuthorizationDenial denial
    ) {
        DiscordAuthorizationDecision decision = service.authorize(actor, Optional.empty(), request);
        assertFalse(decision.permitted());
        assertEquals(denial, decision.denial());
    }
}
