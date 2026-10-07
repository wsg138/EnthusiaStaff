package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

public final class JdbcPolicyV2EnforcementStore implements PolicyV2EnforcementStore {
    private static final String REGISTER_KIND = "P2_REMEDY_ENFORCEMENT_REGISTER";
    private static final String TRANSITION_KIND = "P2_REMEDY_ENFORCEMENT_TRANSITION";
    private final PolicyV2JdbcSupport support;

    public JdbcPolicyV2EnforcementStore(DataSource dataSource) {
        this.support = new PolicyV2JdbcSupport(dataSource, new PolicyV2JsonCodec());
    }

    @Override
    public PolicyV2RemedyEnforcement register(RegisterRequest request) {
        String hash = support.hash(
                request.caseId(),
                request.remedyId(),
                request.subjectId().toString(),
                request.remedyType().name(),
                request.scope().name(),
                support.write(request.condition()),
                request.actorId().toString(),
                request.occurredAt().toString()
        );
        return support.transaction(
                "Policy v2 remedy enforcement registration failed",
                connection -> register(connection, request, hash)
        );
    }

    @Override
    public Optional<PolicyV2RemedyEnforcement> find(String caseId, String remedyId) {
        return support.transaction(
                "Policy v2 remedy enforcement lookup failed",
                connection -> load(connection, caseId, remedyId, false)
        );
    }

    @Override
    public List<PolicyV2RemedyEnforcement> activeFor(UUID subjectId) {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId must be present");
        }
        return support.transaction(
                "Policy v2 active remedy enforcement lookup failed",
                connection -> activeFor(connection, subjectId)
        );
    }

    @Override
    public PolicyV2RemedyEnforcement transition(TransitionRequest request) {
        String hash = support.hash(
                request.caseId(),
                request.remedyId(),
                Long.toString(request.expectedRevision()),
                request.lifecycle().name(),
                request.actorId().toString(),
                request.reason(),
                request.occurredAt().toString()
        );
        return support.transaction(
                "Policy v2 remedy enforcement transition failed",
                connection -> transition(connection, request, hash)
        );
    }

    private PolicyV2RemedyEnforcement register(
            Connection connection,
            RegisterRequest request,
            String hash
    ) throws SQLException {
        Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
        if (existing.isPresent()) {
            support.requireReplay(existing, REGISTER_KIND, hash, request.operationKey());
            return requireRecord(connection, request.caseId(), request.remedyId(), false);
        }
        CanonicalRemedy canonical = requireCanonical(connection, request.caseId(), request.remedyId());
        requireMatchingRegistration(request, canonical);
        if (load(connection, request.caseId(), request.remedyId(), true).isPresent()) {
            throw new PolicyV2Store.Conflict("Policy v2 remedy already has an enforcement record");
        }
        insert(connection, request);
        recordRegistration(connection, request, hash);
        return requireRecord(connection, request.caseId(), request.remedyId(), false);
    }

    private void insert(Connection connection, RegisterRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_remedy_enforcement(
                    case_id, remedy_id, subject_id, remedy_type, scope, condition_json,
                    lifecycle, revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'REQUIRED', 0, ?, ?)
                """)) {
            statement.setString(1, request.caseId());
            statement.setString(2, request.remedyId());
            statement.setBytes(3, UuidBytes.toBytes(request.subjectId()));
            statement.setString(4, request.remedyType().name());
            statement.setString(5, request.scope().name());
            statement.setString(6, support.write(request.condition()));
            statement.setTimestamp(7, Timestamp.from(request.occurredAt()));
            statement.setTimestamp(8, Timestamp.from(request.occurredAt()));
            try {
                statement.executeUpdate();
            } catch (SQLException exception) {
                if (PolicyV2JdbcSupport.isDuplicateKey(exception)) {
                    throw new PolicyV2Store.Conflict("Policy v2 remedy already has an enforcement record");
                }
                throw exception;
            }
        }
    }

    private void recordRegistration(Connection connection, RegisterRequest request, String hash) throws SQLException {
        support.recordOperation(
                connection,
                request.operationKey(),
                REGISTER_KIND,
                hash,
                request.caseId(),
                reference(request.caseId(), request.remedyId()),
                request.occurredAt()
        );
        support.audit(
                connection,
                request.caseId(),
                "REMEDY_ENFORCEMENT_REQUIRED",
                request.actorId(),
                new LifecycleAudit(
                        request.remedyId(),
                        null,
                        Lifecycle.REQUIRED,
                        0L,
                        "Required remedy entered enforcement"
                ),
                request.occurredAt()
        );
    }

    private PolicyV2RemedyEnforcement transition(
            Connection connection,
            TransitionRequest request,
            String hash
    ) throws SQLException {
        Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
        if (existing.isPresent()) {
            support.requireReplay(existing, TRANSITION_KIND, hash, request.operationKey());
            return requireRecord(connection, request.caseId(), request.remedyId(), false);
        }
        PolicyV2RemedyEnforcement current = requireRecord(
                connection, request.caseId(), request.remedyId(), true);
        requireTransition(current, request);
        update(connection, current, request);
        recordTransition(connection, current, request, hash);
        return new PolicyV2RemedyEnforcement(
                current.caseId(),
                current.remedyId(),
                current.subjectId(),
                current.remedyType(),
                current.scope(),
                current.condition(),
                request.lifecycle(),
                current.revision() + 1L,
                request.occurredAt()
        );
    }

    private static void requireTransition(
            PolicyV2RemedyEnforcement current,
            TransitionRequest request
    ) {
        if (current.revision() != request.expectedRevision()) {
            throw new PolicyV2Store.Conflict("Policy v2 enforcement revision is stale");
        }
        boolean allowed = switch (current.lifecycle()) {
            case REQUIRED -> request.lifecycle() == Lifecycle.ENFORCED || request.lifecycle().terminal();
            case ENFORCED -> request.lifecycle().terminal();
            case SATISFIED, WAIVED -> false;
        };
        if (!allowed) {
            throw new PolicyV2Store.Conflict("Policy v2 enforcement lifecycle transition is invalid");
        }
    }

    private void update(
            Connection connection,
            PolicyV2RemedyEnforcement current,
            TransitionRequest request
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE policy_v2_remedy_enforcement
                SET lifecycle = ?, revision = revision + 1, updated_at = ?
                WHERE case_id = ? AND remedy_id = ? AND revision = ? AND lifecycle = ?
                """)) {
            statement.setString(1, request.lifecycle().name());
            statement.setTimestamp(2, Timestamp.from(request.occurredAt()));
            statement.setString(3, current.caseId());
            statement.setString(4, current.remedyId());
            statement.setLong(5, current.revision());
            statement.setString(6, current.lifecycle().name());
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                throw new PolicyV2Store.Conflict("Policy v2 enforcement transition lost its revision fence");
            }
        }
    }

    private void recordTransition(
            Connection connection,
            PolicyV2RemedyEnforcement current,
            TransitionRequest request,
            String hash
    ) throws SQLException {
        support.recordOperation(
                connection,
                request.operationKey(),
                TRANSITION_KIND,
                hash,
                current.caseId(),
                reference(current.caseId(), current.remedyId()),
                request.occurredAt()
        );
        support.audit(
                connection,
                current.caseId(),
                "REMEDY_ENFORCEMENT_" + request.lifecycle().name(),
                request.actorId(),
                new LifecycleAudit(
                        current.remedyId(),
                        current.lifecycle(),
                        request.lifecycle(),
                        current.revision() + 1L,
                        request.reason()
                ),
                request.occurredAt()
        );
    }

    private CanonicalRemedy requireCanonical(
            Connection connection,
            String caseId,
            String remedyId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.target_id, r.remedy_json
                FROM policy_v2_cases v
                JOIN cases c ON c.case_id = v.case_id
                JOIN policy_v2_remedies r ON r.case_id = v.case_id AND r.remedy_id = ?
                WHERE v.case_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, remedyId);
            statement.setString(2, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 case/remedy does not exist");
                }
                return new CanonicalRemedy(
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        support.read(result.getString("remedy_json"), RemedySpec.class)
                );
            }
        }
    }

    private static void requireMatchingRegistration(RegisterRequest request, CanonicalRemedy canonical) {
        if (!canonical.subjectId().equals(request.subjectId())) {
            throw new PolicyV2Store.Conflict("Policy v2 enforcement subject does not match the case target");
        }
        if (canonical.remedy().type() != request.remedyType()
                || !canonical.remedy().id().equals(request.remedyId())) {
            throw new PolicyV2Store.Conflict("Policy v2 enforcement remedy does not match the canonical remedy");
        }
    }

    private Optional<PolicyV2RemedyEnforcement> load(
            Connection connection,
            String caseId,
            String remedyId,
            boolean lock
    ) throws SQLException {
        String sql = """
                SELECT subject_id, remedy_type, scope, condition_json, lifecycle, revision, updated_at
                FROM policy_v2_remedy_enforcement
                WHERE case_id = ? AND remedy_id = ?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId);
            statement.setString(2, remedyId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(read(result, caseId, remedyId))
                        : Optional.empty();
            }
        }
    }

    private PolicyV2RemedyEnforcement requireRecord(
            Connection connection,
            String caseId,
            String remedyId,
            boolean lock
    ) throws SQLException {
        return load(connection, caseId, remedyId, lock)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord(
                        "Policy v2 enforcement record does not exist"));
    }

    private List<PolicyV2RemedyEnforcement> activeFor(Connection connection, UUID subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, remedy_id, subject_id, remedy_type, scope, condition_json,
                       lifecycle, revision, updated_at
                FROM policy_v2_remedy_enforcement
                WHERE subject_id = ? AND lifecycle IN ('REQUIRED', 'ENFORCED')
                ORDER BY case_id, remedy_id
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId));
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyV2RemedyEnforcement> records = new ArrayList<>();
                while (result.next()) {
                    records.add(read(result, result.getString("case_id"), result.getString("remedy_id")));
                }
                return List.copyOf(records);
            }
        }
    }

    private PolicyV2RemedyEnforcement read(ResultSet result, String caseId, String remedyId) throws SQLException {
        return new PolicyV2RemedyEnforcement(
                caseId,
                remedyId,
                UuidBytes.fromBytes(result.getBytes("subject_id")),
                RemedySpec.Type.valueOf(result.getString("remedy_type")),
                Scope.valueOf(result.getString("scope")),
                support.read(result.getString("condition_json"), Condition.class),
                Lifecycle.valueOf(result.getString("lifecycle")),
                result.getLong("revision"),
                result.getTimestamp("updated_at").toInstant()
        );
    }

    private static String reference(String caseId, String remedyId) {
        return caseId + '/' + remedyId;
    }

    private record CanonicalRemedy(UUID subjectId, RemedySpec remedy) {
    }

    private record LifecycleAudit(
            String remedyId,
            Lifecycle from,
            Lifecycle to,
            long revision,
            String reason
    ) {
    }
}
