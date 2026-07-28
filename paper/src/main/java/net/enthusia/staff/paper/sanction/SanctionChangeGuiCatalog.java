package net.enthusia.staff.paper.sanction;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.CaseState;
import net.enthusia.staff.domain.casefile.SanctionReview;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionStatus;

final class SanctionChangeGuiCatalog {
    private final AuthorizationPolicy authorization;

    SanctionChangeGuiCatalog(AuthorizationPolicy authorization) {
        if (authorization == null) {
            throw new IllegalArgumentException("authorization must be present");
        }
        this.authorization = authorization;
    }

    List<SanctionChangeAction> actions(
            Actor actor,
            CaseReview review,
            Predicate<String> hasPermission,
            String commandName
    ) {
        if (actor == null || review == null || hasPermission == null
                || commandName == null || commandName.isBlank()) {
            return List.of();
        }
        SanctionChangeAction aliasAction = "removepunishment".equalsIgnoreCase(commandName)
                ? null
                : SanctionChangeAccess.aliasAction(commandName);
        return Arrays.stream(SanctionChangeAction.values())
                .filter(action -> aliasAction == null || action == aliasAction)
                .filter(action -> authorization.permits(actor, action.requiredModerationAction()))
                .filter(action -> hasPermission.test(SanctionChangeAccess.permissionFor(action)))
                .filter(action -> relevant(action, review))
                .toList();
    }

    private static boolean relevant(SanctionChangeAction action, CaseReview review) {
        boolean pendingOrActive = review.sanctions().stream().anyMatch(sanction ->
                sanction.status() == SanctionStatus.PENDING || sanction.status() == SanctionStatus.ACTIVE
        );
        boolean revocable = review.sanctions().stream().anyMatch(SanctionReview::active);
        boolean active = review.sanctions().stream()
                .anyMatch(sanction -> sanction.status() == SanctionStatus.ACTIVE);
        return switch (action) {
            case END_EARLY -> pendingOrActive;
            case REVOKE -> revocable;
            case REDUCE_DURATION, REPLACE_EXPIRATION -> active;
            case FULL_OVERTURN -> review.state() != CaseState.FULLY_OVERTURNED;
            case REMOVE_ESCALATION_CONTRIBUTION -> review.punishmentStep()
                    .map(step -> step.escalationContributes())
                    .orElse(false);
            case RESTORE_ESCALATION_CONTRIBUTION -> review.punishmentStep()
                    .map(step -> !step.escalationContributes())
                    .orElse(false);
            case REQUEST_FULL_OVERTURN -> review.state() != CaseState.FULLY_OVERTURNED
                    && review.openOverturnRequest().isEmpty();
            case APPROVE_FULL_OVERTURN, DENY_FULL_OVERTURN -> review.openOverturnRequest().isPresent();
        };
    }
}
