package net.enthusia.staff.domain.policyv2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/**
 * Read-only, fail-closed static audit of a candidate Policy v2 snapshot.
 *
 * <p>A passing result does not authorize cutover. External provider receipts,
 * profile evidence, approval, shadow comparisons, migration and rollback
 * rehearsals still require independent operational verification.</p>
 */
public final class PolicyV2StaticSafetyAudit {
    private static final String BLACKMAIL = "safety.blackmail-extortion";
    private static final String LANGUAGE = "chat.language.non-english-public";
    private static final String CONTEXT = "coercion-context";
    private static final String VERIFIED = "real-world-leverage-verified";
    private static final IncidentAttributeValue.EnumValue REAL_WORLD =
            new IncidentAttributeValue.EnumValue("real-world");
    private static final IncidentAttributeValue.BooleanValue AFFIRMED =
            new IncidentAttributeValue.BooleanValue(true);

    private PolicyV2StaticSafetyAudit() {
    }

    public static Report inspect(PolicySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<Finding> findings = new ArrayList<>();
        for (OffensePolicy offense : snapshot.offenses()) {
            boolean contextDefined = BLACKMAIL.equals(offense.id()) && validBlackmailAttributes(offense);
            if (BLACKMAIL.equals(offense.id()) && !contextDefined) {
                findings.add(new Finding("blackmail.missing-real-world-evidence-fields", offense.id(), "", ""));
            }
            for (ResolutionRule rule : offense.rules()) {
                inspectRemedies(findings, offense, rule);
                if (BLACKMAIL.equals(offense.id())
                        && isPunitive(rule.action())
                        && (!contextDefined || !strictRealWorldPredicate(rule.condition()))) {
                    findings.add(new Finding("blackmail.ungated-punitive-rule", offense.id(), rule.id(), ""));
                }
                if (LANGUAGE.equals(offense.id()) && includesNetworkBan(rule.action())) {
                    findings.add(new Finding("language.network-ban-prohibited", offense.id(), rule.id(), ""));
                }
            }
        }
        return new Report(snapshot.version(), findings);
    }

    private static void inspectRemedies(List<Finding> findings, OffensePolicy offense, ResolutionRule rule) {
        for (RemedySpec remedy : rule.remedies()) {
            if (remedy.type() == RemedySpec.Type.OTHER) {
                findings.add(new Finding("remedy.unsupported-other", offense.id(), rule.id(), remedy.id()));
            } else if (remedy.enforcementBinding().isEmpty()) {
                findings.add(new Finding("remedy.missing-enforcement-binding",
                        offense.id(), rule.id(), remedy.id()));
            }
        }
    }

    private static boolean validBlackmailAttributes(OffensePolicy offense) {
        boolean context = offense.attributes().stream().anyMatch(attribute ->
                CONTEXT.equals(attribute.id())
                        && attribute.required()
                        && attribute.kind() == IncidentAttributeDefinition.Kind.ENUM
                        && attribute.allowedValues().containsAll(
                                Set.of("game-only", "real-world", "uncertain")));
        boolean verified = offense.attributes().stream().anyMatch(attribute ->
                VERIFIED.equals(attribute.id())
                        && attribute.required()
                        && attribute.kind() == IncidentAttributeDefinition.Kind.BOOLEAN);
        return context && verified;
    }

    private static boolean strictRealWorldPredicate(RuleCondition condition) {
        return Set.of(REAL_WORLD).equals(condition.acceptedValues().get(CONTEXT))
                && Set.of(AFFIRMED).equals(condition.acceptedValues().get(VERIFIED));
    }

    private static boolean isPunitive(PolicyAction action) {
        return action instanceof PolicyAction.Exact
                || action instanceof PolicyAction.ExactWithApproval
                || action instanceof PolicyAction.Bounded;
    }

    private static boolean includesNetworkBan(PolicyAction action) {
        if (action instanceof PolicyAction.Exact exact) {
            return hasNetworkBan(exact.sanctions());
        }
        if (action instanceof PolicyAction.ExactWithApproval exact) {
            return hasNetworkBan(exact.sanctions());
        }
        if (action instanceof PolicyAction.Bounded bounded) {
            return bounded.allowedOptions().stream().anyMatch(PolicyV2StaticSafetyAudit::hasNetworkBan);
        }
        return false;
    }

    private static boolean hasNetworkBan(List<SanctionSpec> sanctions) {
        return sanctions.stream().anyMatch(sanction -> sanction.type().isBan());
    }

    /** No private evidence or identifying incident metadata is exposed here. */
    public record Finding(String code, String offenseId, String ruleId, String remedyId) {
        public Finding {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(offenseId, "offenseId");
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(remedyId, "remedyId");
        }
    }

    public record Report(String snapshotVersion, List<Finding> findings) {
        public Report {
            Objects.requireNonNull(snapshotVersion, "snapshotVersion");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }

        /** Static checks only; never a production-readiness or deployment decision. */
        public boolean passesStaticChecks() {
            return findings.isEmpty();
        }
    }
}
