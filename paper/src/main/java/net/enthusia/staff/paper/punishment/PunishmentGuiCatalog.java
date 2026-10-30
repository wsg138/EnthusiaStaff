package net.enthusia.staff.paper.punishment;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
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

    List<PunishmentGuiCategory> categories(Actor actor, String commandName) {
        List<ReasonPolicy> visible = available(actor, commandName);
        return PunishmentGuiCategory.ordered().stream()
                .filter(category -> visible.stream().anyMatch(policy -> category.includes(policy.family())))
                .toList();
    }

    List<ReasonPolicy> reasons(Actor actor, String commandName, String categoryId) {
        PunishmentGuiCategory category = PunishmentGuiCategory.byId(categoryId);
        if (category == null) {
            return List.of();
        }
        return available(actor, commandName).stream()
                .filter(policy -> category.includes(policy.family()))
                .sorted(Comparator.comparing(ReasonPolicy::publicReason).thenComparing(ReasonPolicy::id))
                .toList();
    }

    /**
     * Search is a pure presentation filter over the *same* rank/command-limited
     * manual reasons that the category picker uses. It never reads history.
     */
    List<ReasonPolicy> search(Actor actor, String commandName, String query) {
        String normalized = normalizeQuery(query);
        if (normalized.codePointCount(0, normalized.length()) < 2
                || normalized.codePointCount(0, normalized.length()) > 64) {
            return List.of();
        }
        return available(actor, commandName).stream()
                .map(policy -> new RankedResult(policy, searchRank(policy, normalized)))
                .filter(result -> result.rank() < 4)
                .sorted(Comparator.comparingInt(RankedResult::rank)
                        .thenComparing(result -> result.policy().publicReason())
                        .thenComparing(result -> result.policy().id()))
                .map(RankedResult::policy)
                .toList();
    }

    private static int searchRank(ReasonPolicy policy, String query) {
        String id = policy.id().toLowerCase(Locale.ROOT);
        String name = policy.publicReason().toLowerCase(Locale.ROOT);
        if (id.equals(query) || name.equals(query)) {
            return 0;
        }
        if (id.startsWith(query) || name.startsWith(query)) {
            return 1;
        }
        if (id.contains(query) || name.contains(query)) {
            return 2;
        }
        return matchesContext(policy, query) ? 3 : 4;
    }

    private static boolean matchesContext(ReasonPolicy policy, String query) {
        if (policy.family().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return policy.examples().stream()
                .anyMatch(example -> example.toLowerCase(Locale.ROOT).contains(query));
    }

    private record RankedResult(ReasonPolicy policy, int rank) {
    }

    private static String normalizeQuery(String query) {
        return query == null ? "" : query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    String categoryId(String family) {
        return PunishmentGuiCategory.forFamily(family).id();
    }

    Optional<ReasonPolicyRepository.ReasonDescriptor> describe(String reasonId) {
        return policies.describe(reasonId);
    }

    Optional<ReasonPolicy> find(String reasonId) {
        return policies.find(reasonId);
    }

    String activeVersion() {
        return policies.activeVersion();
    }

    /** The same live rank and command filters used by the reason picker.
     * Saved drafts do not retain authority when a staff role changes.
     */
    boolean maySelectReason(Actor actor, String commandName, String reasonId) {
        return reasonId != null && available(actor, commandName).stream()
                .anyMatch(policy -> reasonId.equals(policy.id()));
    }

    private List<ReasonPolicy> available(Actor actor, String commandName) {
        boolean mayIssue = authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION);
        boolean mayRequest = authorization.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION);
        if (!mayIssue && !mayRequest) {
            return List.of();
        }
        return policies.all().stream()
                .filter(policy -> !policy.family().startsWith("cheating.polar")
                        && !policy.id().startsWith("cheating.polar."))
                .filter(policy -> visibleAtRank(actor.rank(), policy.requiredRank(), mayRequest))
                .filter(policy -> PunishmentCommandFilter.includes(commandName, policy))
                .toList();
    }

    private static boolean visibleAtRank(StaffRank actorRank, StaffRank requiredRank, boolean mayRequest) {
        if (actorRank == StaffRank.DEVELOPER) {
            return mayRequest;
        }
        if (actorRank == StaffRank.HELPER && requiredRank == StaffRank.MOD) {
            return true;
        }
        return actorRank.atLeast(requiredRank);
    }
}
