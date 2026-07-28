package net.enthusia.staff.paper.punishment;

import java.util.Comparator;
import java.util.List;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;

final class PunishmentGuiCatalog {
    private final ReasonPolicyRepository policies;
    private final AuthorizationPolicy authorization;

    PunishmentGuiCatalog(ReasonPolicyRepository policies, AuthorizationPolicy authorization) {
        if (policies == null || authorization == null) {
            throw new IllegalArgumentException("punishment GUI catalog dependencies must be present");
        }
        this.policies = policies;
        this.authorization = authorization;
    }

    List<String> categories(Actor actor, String commandName) {
        return available(actor, commandName).stream()
                .map(ReasonPolicy::family)
                .distinct()
                .sorted()
                .toList();
    }

    List<ReasonPolicy> reasons(Actor actor, String commandName, String family) {
        return available(actor, commandName).stream()
                .filter(policy -> policy.family().equals(family))
                .sorted(Comparator.comparing(ReasonPolicy::publicReason).thenComparing(ReasonPolicy::id))
                .toList();
    }

    private List<ReasonPolicy> available(Actor actor, String commandName) {
        if (!authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)) {
            return List.of();
        }
        return policies.all().stream()
                .filter(policy -> actor.rank().atLeast(policy.requiredRank()))
                .filter(policy -> PunishmentCommandFilter.includes(commandName, policy))
                .toList();
    }
}
