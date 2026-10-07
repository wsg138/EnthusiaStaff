package net.enthusia.staff.domain.policyv2.enforcement;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.RemedySpec;

public record PolicyV2RemedyEnforcement(
        String caseId,
        String remedyId,
        UUID subjectId,
        RemedySpec.Type remedyType,
        Scope scope,
        Condition condition,
        Lifecycle lifecycle,
        long revision,
        Instant updatedAt
) {
    private static final long MINIMUM_REVISION = 0L;

    public PolicyV2RemedyEnforcement {
        caseId = requireText(caseId, "case id", 64);
        remedyId = requireText(remedyId, "remedy id", 96);
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(remedyType, "remedyType");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < MINIMUM_REVISION) {
            throw new IllegalArgumentException("enforcement revision must not be negative");
        }
    }

    public boolean active() {
        return lifecycle == Lifecycle.REQUIRED || lifecycle == Lifecycle.ENFORCED;
    }

    public enum Lifecycle {
        REQUIRED,
        ENFORCED,
        SATISFIED,
        WAIVED;

        public boolean terminal() {
            return this == SATISFIED || this == WAIVED;
        }
    }

    public enum Scope {
        NETWORK_ACCESS,
        REPORT_SUBMISSION,
        MARKET_ACCESS,
        REPUTATION_ACCESS,
        CONTENT,
        ASSET
    }

    public enum ConditionType {
        USERNAME,
        PROFILE_COMPONENT,
        VPN_APPROVAL,
        MANUAL
    }

    public record Condition(
            ConditionType type,
            Optional<String> component,
            Optional<String> prohibitedValue
    ) {
        public Condition {
            Objects.requireNonNull(type, "type");
            component = normalize(component, "component", 64);
            prohibitedValue = normalize(prohibitedValue, "prohibited value", 512);
            validateShape(type, component, prohibitedValue);
        }

        public static Condition username(String prohibitedUsername) {
            return new Condition(
                    ConditionType.USERNAME,
                    Optional.empty(),
                    Optional.of(requireText(prohibitedUsername, "prohibited username", 64))
            );
        }

        public static Condition profileComponent(String component, String prohibitedValue) {
            return new Condition(
                    ConditionType.PROFILE_COMPONENT,
                    Optional.of(requireText(component, "profile component", 64)),
                    Optional.of(requireText(prohibitedValue, "prohibited profile value", 512))
            );
        }

        public static Condition vpnApproval() {
            return new Condition(ConditionType.VPN_APPROVAL, Optional.empty(), Optional.empty());
        }

        public static Condition manual() {
            return new Condition(ConditionType.MANUAL, Optional.empty(), Optional.empty());
        }

        private static Optional<String> normalize(Optional<String> value, String field, int maximumLength) {
            Objects.requireNonNull(value, field);
            return value.map(item -> requireText(item, field, maximumLength));
        }

        private static void validateShape(
                ConditionType type,
                Optional<String> component,
                Optional<String> prohibitedValue
        ) {
            boolean noDetails = component.isEmpty() && prohibitedValue.isEmpty();
            boolean valueOnly = component.isEmpty() && prohibitedValue.isPresent();
            boolean componentAndValue = component.isPresent() && prohibitedValue.isPresent();
            boolean valid = switch (type) {
                case USERNAME -> valueOnly;
                case PROFILE_COMPONENT -> componentAndValue;
                case VPN_APPROVAL, MANUAL -> noDetails;
            };
            if (!valid) {
                throw new IllegalArgumentException("enforcement condition shape does not match its type");
            }
        }
    }

    static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds " + maximumLength);
        }
        return value.trim();
    }
}
