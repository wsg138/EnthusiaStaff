package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordAuthorizationRequest;
import net.enthusia.staff.domain.auth.DiscordAuthorizationSnapshot;
import net.enthusia.staff.domain.auth.DiscordConsequenceIntent;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentConfirmationStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Test
    void wrongActorCannotConsumeAnotherStaffMembersConfirmation() {
        MutableClock clock = new MutableClock(NOW);
        DiscordPunishmentConfirmationStore store = new DiscordPunishmentConfirmationStore(
                clock, Duration.ofMinutes(2), 4
        );
        UUID actorId = UUID.randomUUID();
        UUID token = putIssue(store, actorId);

        assertThrows(SecurityException.class, () -> store.claimForActor(token, UUID.randomUUID()));
        assertEquals(1, store.size());
        store.claimForActor(token, actorId);
        assertEquals(0, store.size());
        assertThrows(IllegalStateException.class, () -> store.claimForActor(token, actorId));
    }

    @Test
    void expiredConfirmationIsRemovedAndRejected() {
        MutableClock clock = new MutableClock(NOW);
        DiscordPunishmentConfirmationStore store = new DiscordPunishmentConfirmationStore(
                clock, Duration.ofSeconds(30), 4
        );
        UUID actorId = UUID.randomUUID();
        UUID token = putIssue(store, actorId);
        clock.advance(Duration.ofSeconds(30));

        assertThrows(IllegalStateException.class, () -> store.claimForActor(token, actorId));
        assertEquals(0, store.size());
    }

    private static UUID putIssue(DiscordPunishmentConfirmationStore store, UUID actorId) {
        SanctionLength length = SanctionLength.instant();
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.WARNING,
                length,
                false,
                false,
                Optional.empty(),
                "Rule violation",
                "",
                0,
                true
        );
        DiscordAuthorizationRequest request = new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                List.of(new DiscordConsequenceIntent(
                        ModerationPlatform.DISCORD,
                        DiscordConsequenceType.WARNING,
                        length,
                        false,
                        false
                ))
        );
        DiscordAuthorizationSnapshot snapshot = new DiscordAuthorizationSnapshot(
                actorId,
                StaffRank.MOD,
                Optional.empty(),
                Optional.empty(),
                request
        );
        return store.put(expires -> new DiscordPunishmentConfirmationStore.Draft(
                DiscordPunishmentConfirmationStore.Kind.ISSUE,
                new DiscordUserId("123"),
                Optional.empty(),
                Optional.of(intent),
                Optional.empty(),
                DiscordPunishmentTermination.NONE,
                snapshot,
                expires
        ));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("test clock is UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
