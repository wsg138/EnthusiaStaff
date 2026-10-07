package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2ProjectionJdbc {
    private static final String PROJECTION_OPERATION = "PUBLIC_PROJECTION";

    private final PolicyV2JdbcSupport support;

    PolicyV2ProjectionJdbc(PolicyV2JdbcSupport support) {
        this.support = support;
    }

    PolicyV2PublicProjection publish(PolicyV2Store.PublishProjectionRequest request) {
        String requestHash = projectionHash(request);
        return support.transaction("Unable to publish Policy v2 public projection", connection -> {
            requirePolicyCase(connection, request.projection().caseId());
            Optional<PolicyV2JdbcSupport.Operation> existing = support.operation(connection, request.operationKey());
            if (existing.isPresent()) {
                support.requireReplay(existing, PROJECTION_OPERATION, requestHash, request.operationKey());
                return loadProjection(connection, request.projection().caseId()).orElseThrow();
            }
            writeProjection(connection, request);
            support.recordOperation(
                    connection,
                    request.operationKey(),
                    PROJECTION_OPERATION,
                    requestHash,
                    request.projection().caseId(),
                    Long.toString(request.projection().revision()),
                    request.occurredAt()
            );
            support.audit(
                    connection,
                    request.projection().caseId(),
                    "PUBLIC_PROJECTION_PUBLISHED",
                    null,
                    request.projection(),
                    request.occurredAt()
            );
            return request.projection();
        });
    }

    Optional<PolicyV2PublicProjection> projection(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        return support.transaction("Unable to read Policy v2 public projection",
                connection -> loadProjection(connection, caseId.trim()));
    }


    private void writeProjection(
            Connection connection,
            PolicyV2Store.PublishProjectionRequest request
    ) throws SQLException {
        if (request.expectedRevision() < 0) {
            insertProjection(connection, request);
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE policy_v2_public_projections
                SET projection_json = ?, revision = ?, updated_at = ?
                WHERE case_id = ? AND revision = ?
                """)) {
            statement.setString(1, support.write(request.projection()));
            statement.setLong(2, request.projection().revision());
            statement.setTimestamp(3, Timestamp.from(request.occurredAt()));
            statement.setString(4, request.projection().caseId());
            statement.setLong(5, request.expectedRevision());
            if (!JdbcTransactionSupport.updatedOne(statement.executeUpdate())) {
                throw new PolicyV2Store.Conflict("Policy v2 public projection revision is stale");
            }
        }
    }

    private void insertProjection(
            Connection connection,
            PolicyV2Store.PublishProjectionRequest request
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_v2_public_projections(case_id, projection_json, revision, updated_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, request.projection().caseId());
            statement.setString(2, support.write(request.projection()));
            statement.setLong(3, request.projection().revision());
            statement.setTimestamp(4, Timestamp.from(request.occurredAt()));
            try {
                statement.executeUpdate();
            } catch (SQLException exception) {
                if (loadProjection(connection, request.projection().caseId()).isPresent()) {
                    throw new PolicyV2Store.Conflict("Policy v2 public projection already exists");
                }
                throw exception;
            }
        }
    }

    private Optional<PolicyV2PublicProjection> loadProjection(
            Connection connection,
            String caseId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT projection_json
                FROM policy_v2_public_projections
                WHERE case_id = ?
                """)) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(support.read(
                        result.getString("projection_json"),
                        PolicyV2PublicProjection.class
                ));
            }
        }
    }


    private void requirePolicyCase(Connection connection, String caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM policy_v2_cases WHERE case_id = ? FOR UPDATE"
        )) {
            statement.setString(1, caseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new PolicyV2Store.MissingRecord("Policy v2 case does not exist");
                }
            }
        }
    }


    private String projectionHash(PolicyV2Store.PublishProjectionRequest request) {
        return support.hash(
                support.write(request.projection()),
                Long.toString(request.expectedRevision()),
                request.occurredAt().toString()
        );
    }

}
