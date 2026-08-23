package net.enthusia.staff.domain.auth;

import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.MOD_BAN;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.MOD_RESTRICTION;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.discord;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.minecraft;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordAuthorizationRequestTest {
    @Test
    void requestRejectsMissingOrImplicitScope() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.VIEW_HISTORY, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                List.of(minecraft(
                        DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false))
        ));
    }

    @Test
    void requestRejectsNullPlatformElementsAsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.VIEW_HISTORY,
                Collections.singleton(null),
                List.of()
        ));
    }

    @Test
    void requestRejectsDuplicatePlatformConsequences() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                List.of(
                        discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false),
                        discord(DiscordConsequenceType.KICK, SanctionLength.instant(), false, false)
                )
        ));
    }

    @Test
    void requestRejectsNullConsequenceElementsAsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                Collections.singletonList(null)
        ));
    }

    @Test
    void consequenceRejectsInvalidPlatformAndDurationShapes() {
        assertThrows(IllegalArgumentException.class, () -> minecraft(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)), false, false));
        assertThrows(IllegalArgumentException.class, () -> discord(
                DiscordConsequenceType.WARNING,
                SanctionLength.temporary(Duration.ofHours(1)), false, false));
        assertThrows(IllegalArgumentException.class, () -> discord(
                DiscordConsequenceType.BAN,
                SanctionLength.permanent(), true, false));
    }

    @Test
    void limitsRejectInvalidHierarchy() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationLimits(
                Duration.ofHours(3), Duration.ofHours(2), MOD_BAN, MOD_RESTRICTION));
    }
}
