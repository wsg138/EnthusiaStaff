package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDecision;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDenial;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordAuthorizationRequest;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.ModerationPlatform;

/** Reauthorizes every D09 private mutation against current non-Discord staff authority. */
final class DiscordInvestigationAuthorization {
    static final class DeniedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final DiscordAuthorizationDenial denial;

        DeniedException(DiscordAuthorizationDenial denial) {
            super("Discord investigation authorization denied: " + denial);
            this.denial = denial;
        }

        DiscordAuthorizationDenial denial() {
            return denial;
        }
    }

    private final DiscordModerationAuthorizationService authorization;

    DiscordInvestigationAuthorization(DiscordAuthorizationLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("authorization limits must be present");
        }
        this.authorization = new DiscordModerationAuthorizationService(limits);
    }

    void require(Actor actor, Optional<Actor> targetStaff, DiscordModerationOperation operation) {
        if (actor == null || targetStaff == null || operation == null || !operation.isMutation()) {
            throw new IllegalArgumentException("investigation mutation authorization is invalid");
        }
        DiscordAuthorizationDecision decision = authorization.authorize(
                actor,
                targetStaff,
                new DiscordAuthorizationRequest(operation, Set.of(ModerationPlatform.DISCORD), List.of())
        );
        if (!decision.permitted()) {
            throw new DeniedException(decision.denial());
        }
    }

    void requireVisibility(Actor actor, InvestigationNote.Visibility visibility) {
        if (actor == null || visibility == null) {
            throw new IllegalArgumentException("note visibility authorization is invalid");
        }
        if (visibility == InvestigationNote.Visibility.MANAGEMENT
                && actor.rank() != StaffRank.ADMIN && actor.rank() != StaffRank.FOUNDER) {
            throw new DeniedException(DiscordAuthorizationDenial.UNAUTHORIZED_OPERATION);
        }
    }
}
