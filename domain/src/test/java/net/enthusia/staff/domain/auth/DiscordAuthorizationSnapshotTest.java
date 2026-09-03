package net.enthusia.staff.domain.auth;

import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.actor;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.discord;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.issue;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordAuthorizationSnapshotTest {
    @Test
    void finalCommitReauthorizationFailsClosedOnStaleActorOrTargetAuthority() {
        DiscordModerationAuthorizationService service = service();
        Actor mod = actor(StaffRank.MOD);
        Actor helperTarget = actor(StaffRank.HELPER);
        DiscordAuthorizationRequest request = issue(discord(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false));
        DiscordAuthorizationSnapshot snapshot = service.captureForConfirmation(
                mod, Optional.of(helperTarget), request).orElseThrow();

        assertTrue(service.reauthorize(snapshot, mod, Optional.of(helperTarget)).permitted());
        assertStale(service, snapshot,
                new Actor(mod.id(), mod.displayName(), StaffRank.ADMIN), Optional.of(helperTarget));
        assertStale(service, snapshot, mod, Optional.of(
                new Actor(helperTarget.id(), helperTarget.displayName(), StaffRank.MOD)));
        assertStale(service, snapshot, mod, Optional.of(actor(StaffRank.HELPER)));
    }

    @Test
    void deniedRequestCannotProduceConfirmationSnapshot() {
        boolean empty = service().captureForConfirmation(
                actor(StaffRank.HELPER),
                Optional.empty(),
                issue(discord(
                        DiscordConsequenceType.BAN,
                        SanctionLength.temporary(Duration.ofHours(1)),
                        false,
                        false
                ))
        ).isEmpty();
        assertTrue(empty);
    }

    private static void assertStale(
            DiscordModerationAuthorizationService service,
            DiscordAuthorizationSnapshot snapshot,
            Actor actor,
            Optional<Actor> target
    ) {
        assertEquals(
                DiscordAuthorizationDenial.STALE_AUTHORIZATION,
                service.reauthorize(snapshot, actor, target).denial()
        );
    }
}
