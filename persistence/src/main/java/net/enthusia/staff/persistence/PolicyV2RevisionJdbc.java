package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2RevisionJdbc {
    private final PolicyV2FindingRevisionJdbc findings;
    private final PolicyV2SanctionRevisionJdbc sanctions;

    PolicyV2RevisionJdbc(PolicyV2JdbcSupport support, PolicyV2CaseReaderJdbc reader) {
        this.findings = new PolicyV2FindingRevisionJdbc(support, reader);
        this.sanctions = new PolicyV2SanctionRevisionJdbc(support, reader);
    }

    List<PolicyV2Store.FindingRevisionRecord> findingRevisions(String caseId, int limit) {
        return findings.findingRevisions(caseId, limit);
    }

    PolicyV2Store.CaseRecord reviseFinding(PolicyV2Store.FindingRevisionRequest request) {
        return findings.reviseFinding(request);
    }

    List<PolicyV2Store.SanctionRevisionRecord> sanctionRevisions(String caseId, int limit) {
        return sanctions.sanctionRevisions(caseId, limit);
    }

    PolicyV2Store.SanctionRevisionRecord reviseSanctions(PolicyV2Store.SanctionRevisionRequest request) {
        return sanctions.reviseSanctions(request);
    }

    void insertInitialSanctions(
            Connection connection,
            PolicyV2Store.CreateCase request,
            String requestHash
    ) throws SQLException {
        sanctions.insertInitialSanctions(connection, request, requestHash);
    }
}
