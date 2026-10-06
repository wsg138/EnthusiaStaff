package net.enthusia.staff.domain.policyv2;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyConfigurationValidatorTest {
    private static final String SPAM = "chat.spam";

    @Test
    void rejectsUnknownRelationshipTargets() {
        OffensePolicy offense = PolicyV2TestFixtures.offense(
                SPAM,
                Map.of("missing.offense", 1.0),
                DecayPolicy.nonDecaying()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicySnapshot("v2.bad", List.of(offense))
        );
    }

    @Test
    void rejectsOverlappingRulesRatherThanDependingOnOrder() {
        ResolutionRule first = new ResolutionRule(
                "first",
                new RuleCondition(Map.of(), new HistoryWindow(0.0, 2.0)),
                PolicyV2TestFixtures.exactWarning(),
                List.of()
        );
        ResolutionRule second = new ResolutionRule(
                "second",
                new RuleCondition(Map.of(), new HistoryWindow(1.0, 3.0)),
                new PolicyAction.RequiresReview("manual.review"),
                List.of()
        );
        OffensePolicy offense = new OffensePolicy(
                SPAM,
                "Spam",
                SPAM,
                List.of(),
                new HistoryPolicy(Map.of(SPAM, 1.0), DecayPolicy.nonDecaying()),
                List.of(first, second)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicySnapshot("v2.bad", List.of(offense))
        );
    }

    @Test
    void rejectsRuleValuesOutsideOffenseSpecificAttributeContract() {
        IncidentAttributeDefinition severity = IncidentAttributeDefinition.enumValue(
                "severity",
                true,
                Set.of("low", "high")
        );
        ResolutionRule rule = new ResolutionRule(
                "invalid",
                new RuleCondition(
                        Map.of(
                                "severity",
                                Set.of(new IncidentAttributeValue.EnumValue("critical"))
                        ),
                        HistoryWindow.atLeast(0.0)
                ),
                PolicyV2TestFixtures.exactWarning(),
                List.of()
        );
        OffensePolicy offense = new OffensePolicy(
                SPAM,
                "Spam",
                SPAM,
                List.of(severity),
                new HistoryPolicy(Map.of(SPAM, 1.0), DecayPolicy.nonDecaying()),
                List.of(rule)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicySnapshot("v2.bad", List.of(offense))
        );
    }

    @Test
    void rejectsMalformedDecayParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DecayPolicy.exponential(Duration.ZERO, 0.5, 2.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DecayPolicy.exponential(Duration.ofDays(1), -0.1, 2.0)
        );
    }

    @Test
    void remedyOnlyActionsCannotLeakIntoPunitiveSanctions() {
        SanctionSpec contentRemoval = new SanctionSpec(
                SanctionType.CONTENT_REMOVAL,
                SanctionLength.instant()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyAction.Exact(List.of(contentRemoval))
        );
    }

    @Test
    void revisionTypesKeepSanctionAndFindingChangesDistinct() {
        CaseRevision sanction = new CaseRevision.SanctionRevision("leniency", List.of());
        CaseRevision finding = new CaseRevision.FindingReclassification(
                SPAM,
                "chat.flood",
                "facts corrected"
        );
        CaseRevision overturn = new CaseRevision.FindingOverturn(
                SPAM,
                "finding unsupported"
        );

        assertInstanceOf(CaseRevision.SanctionRevision.class, sanction);
        assertInstanceOf(CaseRevision.FindingReclassification.class, finding);
        assertInstanceOf(CaseRevision.FindingOverturn.class, overturn);
    }
}
