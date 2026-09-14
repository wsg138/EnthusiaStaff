package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDecision;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDenial;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordAuthorizationRequest;
import net.enthusia.staff.domain.auth.DiscordAuthorizationSnapshot;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.ModerationPlatform;

/** D03-backed authorization for every D07 mutation and final confirmation. */
final class DiscordPunishmentAuthorization {
    static final class DeniedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final DiscordAuthorizationDenial denial;

        DeniedException(DiscordAuthorizationDenial denial) {
            super("Discord punishment authorization denied: " + denial);
            this.denial = denial;
        }

        DiscordAuthorizationDenial denial() {
            return denial;
        }
    }

    private final DiscordModerationAuthorizationService authorization;

    DiscordPunishmentAuthorization(DiscordAuthorizationLimits limits) {
        this.authorization = new DiscordModerationAuthorizationService(limits);
    }

    DiscordAuthorizationSnapshot captureIssue(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordPunishmentIntent intent
    ) {
        return capture(actor, targetStaff, issueRequest(intent));
    }

    DiscordAuthorizationSnapshot captureMutation(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordModerationOperation operation
    ) {
        return capture(actor, targetStaff, operationRequest(operation));
    }

    void reauthorize(
            DiscordAuthorizationSnapshot snapshot,
            Actor currentActor,
            Optional<Actor> currentTargetStaff
    ) {
        requireAllowed(authorization.reauthorize(snapshot, currentActor, currentTargetStaff));
    }

    void requireApprovalOfConcreteSanction(
            Actor requester,
            Actor approver,
            Optional<Actor> targetStaff,
            StaffRank requiredApprovalRank,
            DiscordPunishmentIntent intent
    ) {
        if (requester == null || approver == null || targetStaff == null || requiredApprovalRank == null) {
            throw new IllegalArgumentException("approval authorization fields must be present");
        }
        if (requester.id().equals(approver.id()) || authorityLevel(approver.rank()) < authorityLevel(requiredApprovalRank)) {
            throw new DeniedException(DiscordAuthorizationDenial.UNAUTHORIZED_OPERATION);
        }
        requireAllowed(authorization.authorize(requester, targetStaff, issueRequest(intent)));
        requireAllowed(authorization.authorize(
                approver,
                targetStaff,
                operationRequest(DiscordModerationOperation.APPROVE_SANCTION_REQUEST)
        ));
        requireAllowed(authorization.authorize(approver, targetStaff, issueRequest(intent)));
    }

    private DiscordAuthorizationSnapshot capture(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordAuthorizationRequest request
    ) {
        DiscordAuthorizationDecision decision = authorization.authorize(actor, targetStaff, request);
        requireAllowed(decision);
        return authorization.captureForConfirmation(actor, targetStaff, request).orElseThrow();
    }

    private static DiscordAuthorizationRequest issueRequest(DiscordPunishmentIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("punishment intent must be present");
        }
        return new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                List.of(intent.authorizationIntent())
        );
    }

    private static DiscordAuthorizationRequest operationRequest(DiscordModerationOperation operation) {
        return new DiscordAuthorizationRequest(operation, Set.of(ModerationPlatform.DISCORD), List.of());
    }

    private static int authorityLevel(StaffRank rank) {
        return switch (rank) {
            case HELPER -> 10;
            case MOD, DEVELOPER -> 20;
            case ADMIN -> 30;
            case FOUNDER -> 40;
            case SYSTEM -> 50;
        };
    }

    private static void requireAllowed(DiscordAuthorizationDecision decision) {
        if (!decision.permitted()) {
            throw new DeniedException(decision.denial());
        }
    }
}
