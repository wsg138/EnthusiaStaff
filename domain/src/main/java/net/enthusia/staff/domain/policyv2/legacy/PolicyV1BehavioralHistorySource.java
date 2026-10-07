package net.enthusia.staff.domain.policyv2.legacy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface PolicyV1BehavioralHistorySource {
    List<LegacyFinding> completeHistory(UUID subjectId, Instant asOf);

    static PolicyV1BehavioralHistorySource empty() {
        return (subjectId, asOf) -> List.of();
    }

    record LegacyFinding(
            String caseId,
            Instant occurredAt,
            String exactReasonId
    ) {
        public LegacyFinding {
            if (caseId == null || caseId.isBlank() || occurredAt == null
                    || exactReasonId == null || exactReasonId.isBlank()) {
                throw new IllegalArgumentException("legacy Policy v1 finding fields must be present");
            }
            caseId = caseId.trim();
            exactReasonId = exactReasonId.trim();
        }
    }
}
