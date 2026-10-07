package net.enthusia.staff.domain.policyv2.enforcement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;

public final class PolicyV2AccessEvaluator {
    private final PolicyV2EnforcementStore store;

    public PolicyV2AccessEvaluator(PolicyV2EnforcementStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public Decision evaluate(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        List<Blocker> blockers = new ArrayList<>();
        List<Correction> corrections = new ArrayList<>();
        for (PolicyV2RemedyEnforcement record : store.activeFor(observation.subjectId())) {
            if (record.scope() != Scope.NETWORK_ACCESS) {
                continue;
            }
            if (corrected(record.condition(), observation)) {
                corrections.add(new Correction(record.caseId(), record.remedyId(), record.revision()));
            }
            blockers.add(blocker(record));
        }
        return new Decision(blockers.isEmpty(), blockers, corrections);
    }

    private static boolean corrected(Condition condition, Observation observation) {
        return switch (condition.type()) {
            case USERNAME -> !observation.username().equalsIgnoreCase(condition.prohibitedValue().orElseThrow());
            case PROFILE_COMPONENT -> profileCorrected(condition, observation.reliableProfileComponents());
            case VPN_APPROVAL -> observation.vpnState() == VpnState.CLEAR
                    || observation.vpnState() == VpnState.APPROVED;
            case MANUAL -> false;
        };
    }

    private static boolean profileCorrected(Condition condition, Map<String, String> components) {
        String component = condition.component().orElseThrow();
        String current = components.get(component);
        return current != null && !current.equals(condition.prohibitedValue().orElseThrow());
    }

    private static Blocker blocker(PolicyV2RemedyEnforcement record) {
        String reason = switch (record.condition().type()) {
            case USERNAME -> "Username must be corrected before access is restored.";
            case PROFILE_COMPONENT -> "A prohibited profile component must be corrected before access is restored.";
            case VPN_APPROVAL -> "Disable the unapproved VPN/proxy or obtain approval before joining.";
            case MANUAL -> "An access compliance requirement is still active.";
        };
        return new Blocker(record.caseId(), record.remedyId(), reason);
    }

    public enum VpnState {
        CLEAR,
        APPROVED,
        UNAPPROVED,
        UNKNOWN
    }

    public record Observation(
            UUID subjectId,
            String username,
            VpnState vpnState,
            Map<String, String> reliableProfileComponents
    ) {
        public Observation {
            Objects.requireNonNull(subjectId, "subjectId");
            username = PolicyV2RemedyEnforcement.requireText(username, "username", 64);
            Objects.requireNonNull(vpnState, "vpnState");
            Objects.requireNonNull(reliableProfileComponents, "reliableProfileComponents");
            if (reliableProfileComponents.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                            || entry.getValue() == null || entry.getValue().isBlank())) {
                throw new IllegalArgumentException("profile observations must contain non-blank keys and values");
            }
            reliableProfileComponents = Map.copyOf(reliableProfileComponents);
        }
    }

    public record Decision(boolean allowed, List<Blocker> blockers, List<Correction> corrections) {
        public Decision {
            Objects.requireNonNull(blockers, "blockers");
            Objects.requireNonNull(corrections, "corrections");
            blockers = List.copyOf(blockers);
            corrections = List.copyOf(corrections);
            if (allowed != blockers.isEmpty()) {
                throw new IllegalArgumentException("access decision does not match its blockers");
            }
        }
    }

    public record Blocker(String caseId, String remedyId, String reason) {
        public Blocker {
            caseId = PolicyV2RemedyEnforcement.requireText(caseId, "case id", 64);
            remedyId = PolicyV2RemedyEnforcement.requireText(remedyId, "remedy id", 96);
            reason = PolicyV2RemedyEnforcement.requireText(reason, "block reason", 512);
        }
    }

    public record Correction(String caseId, String remedyId, long expectedRevision) {
        public Correction {
            caseId = PolicyV2RemedyEnforcement.requireText(caseId, "case id", 64);
            remedyId = PolicyV2RemedyEnforcement.requireText(remedyId, "remedy id", 96);
            if (expectedRevision < 0L) {
                throw new IllegalArgumentException("correction revision must not be negative");
            }
        }
    }
}
