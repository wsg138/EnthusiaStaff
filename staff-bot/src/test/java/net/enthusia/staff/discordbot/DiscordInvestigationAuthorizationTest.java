package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import org.junit.jupiter.api.Test;

class DiscordInvestigationAuthorizationTest {
    private static final DiscordAuthorizationLimits LIMITS = new DiscordAuthorizationLimits(
            Duration.ofMinutes(30), Duration.ofHours(12), Duration.ofDays(7), Duration.ofDays(7));

    @Test
    void helpersCanMutateOrdinaryInvestigationStateButCannotResolveEvasionAlerts() {
        DiscordInvestigationAuthorization authorization = new DiscordInvestigationAuthorization(LIMITS);
        Actor helper = actor(StaffRank.HELPER, "11111111-1111-1111-1111-111111111111");

        assertDoesNotThrow(() -> authorization.require(
                helper, Optional.empty(), DiscordModerationOperation.CREATE_INVESTIGATION_CASE));
        assertDoesNotThrow(() -> authorization.require(
                helper, Optional.empty(), DiscordModerationOperation.ADD_NOTE));
        assertDoesNotThrow(() -> authorization.requireVisibility(helper, InvestigationNote.Visibility.STAFF));
        assertThrows(DiscordInvestigationAuthorization.DeniedException.class, () -> authorization.require(
                helper, Optional.empty(), DiscordModerationOperation.RESOLVE_EVASION_ALERT));
        assertThrows(DiscordInvestigationAuthorization.DeniedException.class, () ->
                authorization.requireVisibility(helper, InvestigationNote.Visibility.MANAGEMENT));
    }

    @Test
    void moderatorsCanResolveAlertsButEqualRankTargetProtectionStillApplies() {
        DiscordInvestigationAuthorization authorization = new DiscordInvestigationAuthorization(LIMITS);
        Actor moderator = actor(StaffRank.MOD, "22222222-2222-2222-2222-222222222222");
        Actor equalRankTarget = actor(StaffRank.MOD, "33333333-3333-3333-3333-333333333333");

        assertDoesNotThrow(() -> authorization.require(
                moderator, Optional.empty(), DiscordModerationOperation.RESOLVE_EVASION_ALERT));
        assertThrows(DiscordInvestigationAuthorization.DeniedException.class, () -> authorization.require(
                moderator, Optional.of(equalRankTarget), DiscordModerationOperation.EDIT_NOTE));
    }

    @Test
    void managementVisibilityRequiresAdminOrFounder() {
        DiscordInvestigationAuthorization authorization = new DiscordInvestigationAuthorization(LIMITS);
        assertThrows(DiscordInvestigationAuthorization.DeniedException.class, () -> authorization.requireVisibility(
                actor(StaffRank.DEVELOPER, "44444444-4444-4444-4444-444444444444"),
                InvestigationNote.Visibility.MANAGEMENT));
        assertDoesNotThrow(() -> authorization.requireVisibility(
                actor(StaffRank.ADMIN, "55555555-5555-5555-5555-555555555555"),
                InvestigationNote.Visibility.MANAGEMENT));
    }

    private static Actor actor(StaffRank rank, String id) {
        return new Actor(UUID.fromString(id), rank.name(), rank);
    }
}
