package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

/** MariaDB implementation of the additive Policy v2 persistence contract. */
public final class JdbcPolicyV2Store implements PolicyV2Store {
    private final PolicyV2CaseJdbc cases;
    private final PolicyV2EvaluationJdbc evaluations;

    public JdbcPolicyV2Store(DataSource dataSource) {
        PolicyV2JdbcSupport support = new PolicyV2JdbcSupport(dataSource, new PolicyV2JsonCodec());
        this.cases = new PolicyV2CaseJdbc(support);
        this.evaluations = new PolicyV2EvaluationJdbc(support);
    }

    @Override
    public CaseRecord createCase(CreateCase request) {
        return cases.create(request);
    }

    @Override
    public Optional<CaseRecord> findCase(String caseId) {
        return cases.find(caseId);
    }

    @Override
    public List<BehavioralHistoryEntry> history(UUID subjectId, Instant asOf, int limit) {
        return cases.history(subjectId, asOf, limit);
    }

    @Override
    public CaseRecord reviseFinding(FindingRevisionRequest request) {
        return cases.reviseFinding(request);
    }

    @Override
    public List<FindingRevisionRecord> findingRevisions(String caseId, int limit) {
        return cases.findingRevisions(caseId, limit);
    }

    @Override
    public SanctionRevisionRecord reviseSanctions(SanctionRevisionRequest request) {
        return cases.reviseSanctions(request);
    }

    @Override
    public List<SanctionRevisionRecord> sanctionRevisions(String caseId, int limit) {
        return cases.sanctionRevisions(caseId, limit);
    }

    @Override
    public RemedyRecord updateRemedy(RemedyUpdateRequest request) {
        return cases.updateRemedy(request);
    }

    @Override
    public AppealEvent appendAppealEvent(AppealEventRequest request) {
        return cases.appendAppealEvent(request);
    }

    @Override
    public List<AppealEvent> appealHistory(String caseId, int limit) {
        return cases.appealHistory(caseId, limit);
    }

    @Override
    public List<AuditEvent> auditHistory(String caseId, int limit) {
        return cases.auditHistory(caseId, limit);
    }

    @Override
    public PolicyV2PublicProjection publishProjection(PublishProjectionRequest request) {
        return cases.publish(request);
    }

    @Override
    public Optional<PolicyV2PublicProjection> publicProjection(String caseId) {
        return cases.projection(caseId);
    }

    @Override
    public StoredSnapshot storeSnapshot(PolicySnapshot snapshot, String operationKey, Instant recordedAt) {
        return evaluations.storeSnapshot(snapshot, operationKey, recordedAt);
    }

    @Override
    public PolicySnapshot loadSnapshot(UUID snapshotId) {
        return evaluations.loadSnapshot(snapshotId);
    }

    @Override
    public ShadowEvaluation recordShadowEvaluation(ShadowEvaluationRequest request) {
        return evaluations.recordShadow(request);
    }

    @Override
    public Optional<ShadowEvaluation> shadowEvaluation(UUID evaluationId) {
        return evaluations.shadow(evaluationId);
    }
}
