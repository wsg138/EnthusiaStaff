package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");
    private static final String REQUESTED_SCOPE = "5001";
    private static final String OTHER_SCOPE = "5002";

    @Test
    void exactRestrictionSelectionUsesRequestedScopeInsteadOfNewest() {
        StoredPunishment requested = restriction(REQUESTED_SCOPE);
        StoredPunishment newestDifferentScope = restriction(OTHER_SCOPE);

        StoredPunishment selected = DiscordPunishmentService.selectExactRestriction(
                List.of(newestDifferentScope, requested),
                REQUESTED_SCOPE
        );

        assertEquals(requested.punishment().punishmentId(), selected.punishment().punishmentId());
    }

    @Test
    void exactRestrictionSelectionFailsClosedForMissingOrDuplicateScope() {
        StoredPunishment first = restriction(REQUESTED_SCOPE);
        StoredPunishment duplicate = restriction(REQUESTED_SCOPE);

        assertThrows(IllegalStateException.class, () -> DiscordPunishmentService.selectExactRestriction(
                List.of(first), OTHER_SCOPE
        ));
        assertThrows(IllegalStateException.class, () -> DiscordPunishmentService.selectExactRestriction(
                List.of(first, duplicate), REQUESTED_SCOPE
        ));
    }

    private static StoredPunishment restriction(String scopeId) {
        DiscordPunishmentIntent intent = new DiscordPunishmentIntent(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)),
                false,
                false,
                Optional.of(new DiscordRestrictionTarget(
                        DiscordRestrictionTarget.Kind.CHANNEL,
                        scopeId,
                        DiscordRestrictionTarget.Mode.READ_ONLY
                )),
                "Reason",
                "",
                0,
                true
        );
        DiscordPunishment punishment = DiscordPunishment.pending(
                UUID.randomUUID(),
                new ModerationSubjectId(UUID.randomUUID()),
                new DiscordUserId("123"),
                new DiscordGuildId("456"),
                new Actor(UUID.randomUUID(), "staff", StaffRank.ADMIN),
                intent,
                NOW,
                "issue"
        );
        return new StoredPunishment(punishment, 0, false);
    }
}
