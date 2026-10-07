package net.enthusia.staff.domain.policyv2;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

final class PolicyV2TestFixtures {
    private PolicyV2TestFixtures() {
    }

    static SanctionSpec warning() {
        return new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
    }

    static PolicyAction exactWarning() {
        return new PolicyAction.Exact(List.of(warning()));
    }

    static ResolutionRule catchAll(String id, PolicyAction action) {
        return new ResolutionRule(
                id,
                new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                action,
                List.of()
        );
    }

    static OffensePolicy offense(
            String id,
            Map<String, Double> relationships,
            DecayPolicy decay
    ) {
        return new OffensePolicy(
                id,
                id,
                "test.navigation",
                List.of(),
                new HistoryPolicy(relationships, decay),
                List.of(catchAll("default", exactWarning()))
        );
    }

    static DecayPolicy tenDayDecay() {
        return DecayPolicy.exponential(Duration.ofDays(10), 0.5, 3.0);
    }
}
