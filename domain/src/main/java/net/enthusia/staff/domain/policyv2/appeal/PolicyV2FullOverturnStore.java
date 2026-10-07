package net.enthusia.staff.domain.policyv2.appeal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/** Durable checkpoint store for one logical Policy v2 full-overturn operation. */
public interface PolicyV2FullOverturnStore {
    Operation begin(BeginRequest request);

    Optional<Operation> find(UUID operationId);

    Operation advance(
            UUID operationId,
            long expectedRevision,
            Stage expectedStage,
            Stage nextStage,
            Instant occurredAt
    );

    enum Stage {
        STARTED,
        FINDING_OVERTURNED,
        SANCTIONS_TERMINATED,
        REMEDIES_CLEANED,
        COMPLETED
    }

    record Plan(
            Optional<String> findingOffenseId,
            long findingRevision,
            long sanctionRevision,
            List<SanctionSpec> sanctions,
            List<RemedyTarget> remedies
    ) {
        public Plan {
            if (findingOffenseId == null || findingRevision < 0 || sanctionRevision < 0
                    || sanctions == null || remedies == null
                    || sanctions.stream().anyMatch(java.util.Objects::isNull)
                    || remedies.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("full-overturn plan is invalid");
            }
            findingOffenseId = findingOffenseId.map(value -> requireText(value, "finding offense id", 96));
            sanctions = List.copyOf(sanctions);
            remedies = List.copyOf(remedies);
        }
    }

    record RemedyTarget(
            String remedyId,
            long canonicalRevision,
            Optional<Long> enforcementRevision,
            boolean cleanupRequired
    ) {
        public RemedyTarget {
            remedyId = requireText(remedyId, "remedy id", 96);
            if (canonicalRevision < 0 || enforcementRevision == null
                    || enforcementRevision.filter(value -> value < 0).isPresent()
                    || (cleanupRequired && enforcementRevision.isEmpty())) {
                throw new IllegalArgumentException("full-overturn remedy target is invalid");
            }
        }
    }

    record BeginRequest(
            UUID operationId,
            String caseId,
            String appealReference,
            UUID actorId,
            String reason,
            Plan plan,
            Instant occurredAt
    ) {
        public BeginRequest {
            if (operationId == null || actorId == null || plan == null || occurredAt == null) {
                throw new IllegalArgumentException("full-overturn request fields must be present");
            }
            caseId = requireText(caseId, "case id", 64);
            appealReference = requireText(appealReference, "appeal reference", 128);
            reason = requireText(reason, "full-overturn reason", 1_000);
        }
    }

    record Operation(
            UUID operationId,
            String caseId,
            String appealReference,
            UUID actorId,
            String reason,
            Plan plan,
            Stage stage,
            long revision,
            Instant createdAt,
            Instant updatedAt
    ) {
        public Operation {
            if (operationId == null || actorId == null || plan == null || stage == null
                    || revision < 0 || createdAt == null || updatedAt == null) {
                throw new IllegalArgumentException("full-overturn operation is invalid");
            }
            caseId = requireText(caseId, "case id", 64);
            appealReference = requireText(appealReference, "appeal reference", 128);
            reason = requireText(reason, "full-overturn reason", 1_000);
        }

        public boolean completed() {
            return stage == Stage.COMPLETED;
        }
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds " + maximumLength);
        }
        return value.trim();
    }
}
