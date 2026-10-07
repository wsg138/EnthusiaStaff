package net.enthusia.staff.domain.policyv2.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.CaseRevision;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/**
 * Durable Policy v2 boundary. Policy v1 remains authoritative unless a later
 * cutover explicitly selects v2; absence of a v2 record means legacy/unknown.
 */
public interface PolicyV2Store {
    CaseRecord createCase(CreateCase request);

    Optional<CaseRecord> findCase(String caseId);

    /**
     * Complete authoritative Policy v2 behavioral history for resolver input.
     * This method must never silently truncate semantically relevant entries.
     */
    List<BehavioralHistoryEntry> completeHistory(UUID subjectId, Instant asOf);

    /**
     * Bounded history for human-facing or paginated views only. Never use this
     * method as authoritative input to PolicyResolver.
     */
    List<BehavioralHistoryEntry> history(UUID subjectId, Instant asOf, int limit);

    CaseRecord reviseFinding(FindingRevisionRequest request);

    List<FindingRevisionRecord> findingRevisions(String caseId, int limit);

    SanctionRevisionRecord reviseSanctions(SanctionRevisionRequest request);

    List<SanctionRevisionRecord> sanctionRevisions(String caseId, int limit);

    RemedyRecord updateRemedy(RemedyUpdateRequest request);

    AppealEvent appendAppealEvent(AppealEventRequest request);

    List<AppealEvent> appealHistory(String caseId, int limit);

    List<AuditEvent> auditHistory(String caseId, int limit);

    PolicyV2PublicProjection publishProjection(PublishProjectionRequest request);

    Optional<PolicyV2PublicProjection> publicProjection(String caseId);

    StoredSnapshot storeSnapshot(PolicySnapshot snapshot, String operationKey, Instant recordedAt);

    PolicySnapshot loadSnapshot(UUID snapshotId);

    ShadowEvaluation recordShadowEvaluation(ShadowEvaluationRequest request);

    Optional<ShadowEvaluation> shadowEvaluation(UUID evaluationId);

    enum RemedyStatus {
        REQUIRED,
        SATISFIED,
        WAIVED
    }

    enum SanctionChangeKind {
        INITIAL,
        LENIENCY,
        OTHER
    }

    enum FindingChangeKind {
        RECLASSIFICATION,
        OVERTURN
    }

    enum AppealEventType {
        LINKED,
        SUBMITTED,
        INFORMATION_REQUESTED,
        APPROVED,
        DENIED,
        WITHDRAWN,
        REVISION_APPLIED,
        OTHER
    }

    record CreateCase(
            String caseId,
            PolicySnapshot policySnapshot,
            IncidentFinding finding,
            Instant incidentAt,
            PolicyResolution resolution,
            List<BehavioralHistoryEntry> historyInputs,
            List<SanctionSpec> appliedSanctions,
            UUID actorId,
            String operationKey,
            Instant recordedAt
    ) {
        public CreateCase {
            caseId = requireCaseId(caseId);
            operationKey = requireOperationKey(operationKey);
            if (policySnapshot == null || finding == null || incidentAt == null || resolution == null
                    || historyInputs == null || appliedSanctions == null || actorId == null || recordedAt == null) {
                throw new IllegalArgumentException("Policy v2 case inputs must be present");
            }
            if (!policySnapshot.version().equals(resolution.policyVersion())
                    || !finding.offenseId().equals(resolution.offenseId())) {
                throw new IllegalArgumentException("finding and resolution must use the supplied policy snapshot");
            }
            if (incidentAt.isAfter(recordedAt)
                    || historyInputs.stream().anyMatch(java.util.Objects::isNull)
                    || appliedSanctions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Policy v2 case history, sanctions, or timestamps are invalid");
            }
            historyInputs = List.copyOf(historyInputs);
            appliedSanctions = List.copyOf(appliedSanctions);
        }
    }

    record CaseRecord(
            String caseId,
            UUID policySnapshotId,
            UUID resolutionId,
            IncidentFinding originalFinding,
            Optional<IncidentFinding> effectiveFinding,
            BehavioralHistoryEntry.FindingState findingState,
            Instant incidentAt,
            long findingRevision,
            long sanctionRevision,
            PolicyResolution resolution,
            List<BehavioralHistoryEntry> historyInputs,
            List<RemedyRecord> remedies,
            SanctionRevisionRecord currentSanctions
    ) {
        public CaseRecord {
            caseId = requireCaseId(caseId);
            if (policySnapshotId == null || resolutionId == null || originalFinding == null
                    || effectiveFinding == null || findingState == null || incidentAt == null
                    || findingRevision < 0 || sanctionRevision < 0 || resolution == null
                    || historyInputs == null || remedies == null || currentSanctions == null) {
                throw new IllegalArgumentException("Policy v2 case record is invalid");
            }
            if ((findingState == BehavioralHistoryEntry.FindingState.OVERTURNED) == effectiveFinding.isPresent()) {
                throw new IllegalArgumentException("effective finding must be absent only after overturn");
            }
            historyInputs = List.copyOf(historyInputs);
            remedies = List.copyOf(remedies);
        }

        public BehavioralHistoryEntry historyEntry() {
            return new BehavioralHistoryEntry(
                    caseId,
                    incidentAt,
                    originalFinding.offenseId(),
                    effectiveFinding.map(IncidentFinding::offenseId).orElse(null),
                    findingState
            );
        }
    }

    record FindingRevisionRequest(
            String caseId,
            long expectedFindingRevision,
            CaseRevision revision,
            Optional<IncidentFinding> replacementFinding,
            UUID actorId,
            Optional<String> appealReference,
            String operationKey,
            Instant occurredAt
    ) {
        public FindingRevisionRequest {
            caseId = requireCaseId(caseId);
            operationKey = requireOperationKey(operationKey);
            if (expectedFindingRevision < 0 || revision == null || replacementFinding == null
                    || actorId == null || appealReference == null || occurredAt == null) {
                throw new IllegalArgumentException("finding revision request is invalid");
            }
            appealReference = normalizeAppealReference(appealReference);
            validateFindingRevision(revision, replacementFinding);
        }

        private static void validateFindingRevision(
                CaseRevision revision,
                Optional<IncidentFinding> replacementFinding
        ) {
            if (revision instanceof CaseRevision.FindingReclassification reclassification) {
                if (replacementFinding.isEmpty()
                        || !reclassification.toOffenseId().equals(replacementFinding.orElseThrow().offenseId())) {
                    throw new IllegalArgumentException("reclassification requires the replacement finding");
                }
                return;
            }
            if (!(revision instanceof CaseRevision.FindingOverturn) || replacementFinding.isPresent()) {
                throw new IllegalArgumentException("finding revisions must reclassify or overturn");
            }
        }
    }

    record FindingRevisionRecord(
            String caseId,
            long revision,
            FindingChangeKind changeKind,
            String fromOffenseId,
            Optional<String> toOffenseId,
            Optional<IncidentFinding> resultingFinding,
            String reason,
            UUID actorId,
            Optional<String> appealReference,
            Instant occurredAt
    ) {
        public FindingRevisionRecord {
            caseId = requireCaseId(caseId);
            fromOffenseId = requireText(fromOffenseId, "source offense id");
            reason = requireText(reason, "finding revision reason");
            if (revision < 1 || changeKind == null || toOffenseId == null || resultingFinding == null
                    || actorId == null || appealReference == null || occurredAt == null) {
                throw new IllegalArgumentException("finding revision record is invalid");
            }
            toOffenseId = normalizeOptionalText(toOffenseId, "replacement offense id");
            appealReference = normalizeAppealReference(appealReference);
            boolean overturned = changeKind == FindingChangeKind.OVERTURN;
            boolean hasReplacement = toOffenseId.isPresent() && resultingFinding.isPresent();
            if (overturned ? hasReplacement || toOffenseId.isPresent() || resultingFinding.isPresent()
                    : !hasReplacement) {
                throw new IllegalArgumentException("finding revision shape does not match its change kind");
            }
        }
    }

    record SanctionRevisionRequest(
            String caseId,
            long expectedSanctionRevision,
            SanctionChangeKind changeKind,
            CaseRevision.SanctionRevision revision,
            UUID actorId,
            Optional<String> appealReference,
            String operationKey,
            Instant occurredAt
    ) {
        public SanctionRevisionRequest {
            caseId = requireCaseId(caseId);
            operationKey = requireOperationKey(operationKey);
            if (expectedSanctionRevision < 0 || changeKind == null || changeKind == SanctionChangeKind.INITIAL
                    || revision == null || actorId == null || appealReference == null || occurredAt == null) {
                throw new IllegalArgumentException("sanction revision request is invalid");
            }
            appealReference = normalizeAppealReference(appealReference);
        }
    }

    record SanctionRevisionRecord(
            String caseId,
            long revision,
            SanctionChangeKind changeKind,
            List<SanctionSpec> sanctions,
            String reason,
            UUID actorId,
            Optional<String> appealReference,
            Instant occurredAt
    ) {
        public SanctionRevisionRecord {
            caseId = requireCaseId(caseId);
            reason = requireText(reason, "sanction revision reason");
            if (revision < 0 || changeKind == null || sanctions == null || actorId == null
                    || appealReference == null || occurredAt == null
                    || sanctions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("sanction revision record is invalid");
            }
            appealReference = normalizeAppealReference(appealReference);
            sanctions = List.copyOf(sanctions);
        }
    }

    record RemedyRecord(
            String caseId,
            RemedySpec remedy,
            RemedyStatus status,
            long revision,
            Instant updatedAt
    ) {
        public RemedyRecord {
            caseId = requireCaseId(caseId);
            if (remedy == null || status == null || revision < 0 || updatedAt == null) {
                throw new IllegalArgumentException("remedy record is invalid");
            }
        }
    }

    record RemedyUpdateRequest(
            String caseId,
            String remedyId,
            long expectedRevision,
            RemedyStatus status,
            UUID actorId,
            String reason,
            String operationKey,
            Instant occurredAt
    ) {
        public RemedyUpdateRequest {
            caseId = requireCaseId(caseId);
            remedyId = requireText(remedyId, "remedy id");
            reason = requireText(reason, "remedy update reason");
            operationKey = requireOperationKey(operationKey);
            if (expectedRevision < 0 || status == null || actorId == null || occurredAt == null) {
                throw new IllegalArgumentException("remedy update request is invalid");
            }
        }
    }

    record AppealEventRequest(
            String caseId,
            String appealReference,
            AppealEventType eventType,
            Optional<UUID> actorId,
            String note,
            String operationKey,
            Instant occurredAt
    ) {
        public AppealEventRequest {
            caseId = requireCaseId(caseId);
            appealReference = requireAppealReference(appealReference);
            note = requireText(note, "appeal note");
            operationKey = requireOperationKey(operationKey);
            if (eventType == null || actorId == null || occurredAt == null) {
                throw new IllegalArgumentException("appeal event request is invalid");
            }
        }
    }

    record AppealEvent(
            UUID eventId,
            String caseId,
            String appealReference,
            AppealEventType eventType,
            Optional<UUID> actorId,
            String note,
            Instant occurredAt
    ) {
        public AppealEvent {
            if (eventId == null || actorId == null || eventType == null || occurredAt == null) {
                throw new IllegalArgumentException("appeal event is invalid");
            }
            caseId = requireCaseId(caseId);
            appealReference = requireAppealReference(appealReference);
            note = requireText(note, "appeal note");
        }
    }

    record AuditEvent(
            UUID auditId,
            String caseId,
            String eventType,
            Optional<UUID> actorId,
            Instant occurredAt
    ) {
        public AuditEvent {
            if (auditId == null || actorId == null || occurredAt == null) {
                throw new IllegalArgumentException("audit event is invalid");
            }
            caseId = requireCaseId(caseId);
            eventType = requireText(eventType, "event type");
        }
    }

    record PublishProjectionRequest(
            PolicyV2PublicProjection projection,
            long expectedRevision,
            String operationKey,
            Instant occurredAt
    ) {
        public PublishProjectionRequest {
            operationKey = requireOperationKey(operationKey);
            if (projection == null || expectedRevision < -1 || occurredAt == null) {
                throw new IllegalArgumentException("public projection request is invalid");
            }
            long requiredRevision = expectedRevision < 0 ? 0 : expectedRevision + 1;
            if (projection.revision() != requiredRevision) {
                throw new IllegalArgumentException("public projection revision must advance exactly once");
            }
        }
    }

    record StoredSnapshot(
            UUID snapshotId,
            String policyVersion,
            String sha256,
            Instant recordedAt
    ) {
        public StoredSnapshot {
            if (snapshotId == null || recordedAt == null) {
                throw new IllegalArgumentException("stored snapshot identity is invalid");
            }
            policyVersion = requireText(policyVersion, "policy version");
            sha256 = requireText(sha256, "snapshot hash");
        }
    }

    record ShadowEvaluationRequest(
            UUID subjectId,
            Optional<String> caseId,
            PolicySnapshot policySnapshot,
            IncidentFinding finding,
            PolicyResolution resolution,
            List<BehavioralHistoryEntry> historyInputs,
            String operationKey,
            Instant evaluatedAt
    ) {
        public ShadowEvaluationRequest {
            operationKey = requireOperationKey(operationKey);
            if (subjectId == null || caseId == null || policySnapshot == null || finding == null
                    || resolution == null || historyInputs == null || evaluatedAt == null
                    || historyInputs.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("shadow evaluation request is invalid");
            }
            caseId = normalizeCaseId(caseId);
            if (!policySnapshot.version().equals(resolution.policyVersion())
                    || !finding.offenseId().equals(resolution.offenseId())) {
                throw new IllegalArgumentException("shadow finding and resolution must share the policy snapshot");
            }
            historyInputs = List.copyOf(historyInputs);
        }
    }

    record ShadowEvaluation(
            UUID evaluationId,
            UUID subjectId,
            Optional<String> caseId,
            UUID policySnapshotId,
            IncidentFinding finding,
            PolicyResolution resolution,
            List<BehavioralHistoryEntry> historyInputs,
            Instant evaluatedAt
    ) {
        public ShadowEvaluation {
            if (evaluationId == null || subjectId == null || caseId == null || policySnapshotId == null
                    || finding == null || resolution == null || historyInputs == null || evaluatedAt == null) {
                throw new IllegalArgumentException("shadow evaluation is invalid");
            }
            caseId = normalizeCaseId(caseId);
            historyInputs = List.copyOf(historyInputs);
        }
    }

    final class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Conflict(String message) {
            super(message);
        }
    }

    final class MissingRecord extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MissingRecord(String message) {
            super(message);
        }
    }

    private static String requireCaseId(String caseId) {
        return requireText(caseId, "case id");
    }

    private static Optional<String> normalizeCaseId(Optional<String> caseId) {
        return normalizeOptionalText(caseId, "case id");
    }

    private static String requireOperationKey(String operationKey) {
        return requireText(operationKey, "operation key");
    }

    private static String requireAppealReference(String appealReference) {
        return requireText(appealReference, "appeal reference");
    }

    private static Optional<String> normalizeAppealReference(Optional<String> appealReference) {
        return normalizeOptionalText(appealReference, "appeal reference");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    private static Optional<String> normalizeOptionalText(Optional<String> value, String label) {
        return value.map(text -> requireText(text, label));
    }
}
