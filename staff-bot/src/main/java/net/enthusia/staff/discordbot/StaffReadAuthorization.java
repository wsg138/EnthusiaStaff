package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDecision;
import net.enthusia.staff.domain.auth.DiscordAuthorizationLimits;
import net.enthusia.staff.domain.auth.DiscordAuthorizationRequest;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.moderation.ModerationPlatform;

/** D03-backed authorization for D06 read operations only. */
final class StaffReadAuthorization {
    static final class DeniedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DeniedException() {
            super("read-only Discord moderation authorization denied");
        }
    }

    private final DiscordModerationAuthorizationService authorization;

    StaffReadAuthorization() {
        // D06 never creates consequence-bearing requests, so these positive values are unreachable
        // placeholders required by the D03 constructor. D07 must supply real approved runtime ceilings.
        Duration unused = Duration.ofMinutes(1);
        this.authorization = new DiscordModerationAuthorizationService(
                new DiscordAuthorizationLimits(unused, unused, unused, unused)
        );
    }

    void require(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordModerationOperation operation,
            ModerationPlatform platform
    ) {
        if (actor == null || targetStaff == null || operation == null || platform == null) {
            throw new IllegalArgumentException("authorization inputs must be present");
        }
        DiscordAuthorizationDecision decision = authorization.authorize(
                actor,
                targetStaff,
                new DiscordAuthorizationRequest(operation, Set.of(platform), List.of())
        );
        if (!decision.permitted()) {
            throw new DeniedException();
        }
    }
}
