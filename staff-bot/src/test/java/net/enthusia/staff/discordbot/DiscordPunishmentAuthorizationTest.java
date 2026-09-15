package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordPunishmentAuthorizationTest {
    private final DiscordPunishmentAuthorization authorization = new DiscordPunishmentAuthorization(
            new DiscordAuthorizationLimits(
                    Duration.ofMinutes(10),
                    Duration.ofDays(7),
                    Duration.ofDays(30),
                    Duration.ofDays(30)
            )
    );

    @Test
    void moderatorAndDeveloperApprovalCannotBypassPermanentSanctionAuthority() {
        Actor requester = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent permanentBan = ban(SanctionLength.permanent(), false);

        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.MOD), Optional.empty(), StaffRank.MOD, permanentBan));
        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.DEVELOPER), Optional.empty(), StaffRank.MOD, permanentBan));
    }

    @Test
    void moderatorAndDeveloperApprovalCannotBypassCustomConsequenceAuthority() {
        Actor requester = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent customBan = customBan(SanctionLength.temporary(Duration.ofDays(1)));

        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.MOD), Optional.empty(), StaffRank.MOD, customBan));
        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        requester, actor(StaffRank.DEVELOPER), Optional.empty(), StaffRank.MOD, customBan));
    }

    @Test
    void concreteTemporarySanctionIsRecheckedForRequesterAndApprover() {
        Actor requester = actor(StaffRank.MOD);
        Actor approver = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent temporaryBan = ban(SanctionLength.temporary(Duration.ofDays(1)), false);

        assertDoesNotThrow(() -> authorization.requireApprovalOfConcreteSanction(
                requester, approver, Optional.empty(), StaffRank.MOD, temporaryBan));
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        Actor actor = actor(StaffRank.ADMIN);
        assertThrows(DiscordPunishmentAuthorization.DeniedException.class, () ->
                authorization.requireApprovalOfConcreteSanction(
                        actor, actor, Optional.empty(), StaffRank.ADMIN,
                        ban(SanctionLength.permanent(), false)));
    }

    @Test
    void staleConfirmationRankIsRejected() {
        Actor original = actor(StaffRank.ADMIN);
        DiscordPunishmentIntent intent = ban(SanctionLength.permanent(), false);
        var snapshot = authorization.captureIssue(original, Optional.empty(), intent);
        Actor demoted = new Actor(original.id(), original.displayName(), StaffRank.MOD);

        assertThrows(DiscordPunishmentAuthorization.DeniedException.class,
                () -> authorization.reauthorize(snapshot, demoted, Optional.empty()));
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(UUID.randomUUID(), rank.name().toLowerCase(Locale.ROOT), rank);
    }

    private static DiscordPunishmentIntent ban(SanctionLength length, boolean customDuration) {
        return ban(length, customDuration, false);
    }

    private static DiscordPunishmentIntent customBan(SanctionLength length) {
        return ban(length, false, true);
    }

    private static DiscordPunishmentIntent ban(
            SanctionLength length,
            boolean customDuration,
            boolean customConsequence
    ) {
        return new DiscordPunishmentIntent(
                DiscordConsequenceType.BAN,
                length,
                customDuration,
                customConsequence,
                Optional.empty(),
                "Rule violation",
                "",
                0,
                true
        );
    }
}
