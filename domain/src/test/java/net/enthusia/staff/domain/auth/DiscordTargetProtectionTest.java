package net.enthusia.staff.domain.auth;

import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.actor;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.discord;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.issue;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.operation;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordTargetProtectionTest {
    @Test
    void selfTargetIsDeniedForMutations() {
        DiscordModerationAuthorizationService service = service();
        Actor mod = actor(StaffRank.MOD);
        assertEquals(
                DiscordAuthorizationDenial.SELF_TARGET,
                service.authorize(mod, Optional.of(mod), warning()).denial()
        );
    }

    @Test
    void equalOrHigherStaffTargetsAreProtectedWithModDeveloperPeerStatus() {
        DiscordModerationAuthorizationService service = service();
        Map<StaffRank, Integer> levels = levels();
        for (StaffRank actorRank : List.of(
                StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN, StaffRank.FOUNDER)) {
            for (StaffRank targetRank : StaffRank.values()) {
                boolean shouldPass = levels.get(actorRank) > levels.get(targetRank);
                assertEquals(
                        shouldPass,
                        service.authorize(actor(actorRank), Optional.of(actor(targetRank)), warning()).permitted(),
                        actorRank + " -> " + targetRank
                );
            }
        }
    }

    @Test
    void readOnlyInvestigationDoesNotMutateProtectedStaff() {
        DiscordAuthorizationDecision decision = service().authorize(
                actor(StaffRank.HELPER),
                Optional.of(actor(StaffRank.FOUNDER)),
                operation(DiscordModerationOperation.VIEW_HISTORY, ModerationPlatform.DISCORD)
        );
        assertTrue(decision.permitted());
    }

    private static DiscordAuthorizationRequest warning() {
        return issue(discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false));
    }

    private static Map<StaffRank, Integer> levels() {
        return Map.of(
                StaffRank.HELPER, 10,
                StaffRank.MOD, 20,
                StaffRank.DEVELOPER, 20,
                StaffRank.ADMIN, 30,
                StaffRank.FOUNDER, 40,
                StaffRank.SYSTEM, 50
        );
    }
}
