package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2CaseJdbc {
    private final PolicyV2CaseCoreJdbc cases;
    private final PolicyV2RevisionJdbc revisions;
    private final PolicyV2ComplianceJdbc compliance;
    private final PolicyV2ProjectionJdbc projections;

    PolicyV2CaseJdbc(PolicyV2JdbcSupport support) {
        PolicyV2CaseReaderJdbc reader = new PolicyV2CaseReaderJdbc(support);
        this.revisions = new PolicyV2RevisionJdbc(support, reader);
        this.cases = new PolicyV2CaseCoreJdbc(support, reader, revisions);
        this.compliance = new PolicyV2ComplianceJdbc(support);
        this.projections = new PolicyV2ProjectionJdbc(support);
    }

    PolicyV2Store.CaseRecord create(PolicyV2Store.CreateCase request) {
        return cases.create(request);
    }

    Optional<PolicyV2Store.CaseRecord> find(String caseId) {
        return cases.find(caseId);
    }

    List<BehavioralHistoryEntry> completeHistory(UUID subjectId, Instant asOf) {
        return cases.completeHistory(subjectId, asOf);
    }

    List<BehavioralHistoryEntry> history(UUID subjectId, Instant asOf, int limit) {
        return cases.history(subjectId, asOf, limit);
    }

    List<PolicyV2Store.FindingRevisionRecord> findingRevisions(String caseId, int limit) {
        return revisions.findingRevisions(caseId, limit);
    }

    PolicyV2Store.CaseRecord reviseFinding(PolicyV2Store.FindingRevisionRequest request) {
        return revisions.reviseFinding(request);
    }

    List<PolicyV2Store.SanctionRevisionRecord> sanctionRevisions(String caseId, int limit) {
        return revisions.sanctionRevisions(caseId, limit);
    }

    PolicyV2Store.SanctionRevisionRecord reviseSanctions(PolicyV2Store.SanctionRevisionRequest request) {
        return revisions.reviseSanctions(request);
    }

    PolicyV2Store.RemedyRecord updateRemedy(PolicyV2Store.RemedyUpdateRequest request) {
        return compliance.updateRemedy(request);
    }

    List<PolicyV2Store.AppealEvent> appealHistory(String caseId, int limit) {
        return compliance.appealHistory(caseId, limit);
    }

    List<PolicyV2Store.AuditEvent> auditHistory(String caseId, int limit) {
        return compliance.auditHistory(caseId, limit);
    }

    PolicyV2Store.AppealEvent appendAppealEvent(PolicyV2Store.AppealEventRequest request) {
        return compliance.appendAppealEvent(request);
    }

    PolicyV2PublicProjection publish(PolicyV2Store.PublishProjectionRequest request) {
        return projections.publish(request);
    }

    Optional<PolicyV2PublicProjection> projection(String caseId) {
        return projections.projection(caseId);
    }
}
