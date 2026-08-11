package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;

final class ExactSanctionLinkValidator {
    private static final String APPLIED_STATE = "APPLIED";
    private static final String CASE_ID_COLUMN = "case_id";

    private ExactSanctionLinkValidator() {
    }

    static Optional<ExactSanctionChangeResult> validate(
            Connection connection,
            ExactSanctionChangeRequest request,
            ExactSanctionRow row
    ) throws SQLException {
        if (request.linkedAppealId().isPresent()) {
            Optional<ExactSanctionChangeResult> appealFailure = validateAppeal(
                    connection,
                    request.linkedAppealId().orElseThrow(),
                    row
            );
            if (appealFailure.isPresent()) {
                return appealFailure;
            }
        }
        if (request.linkedPunishmentRequestId().isPresent()) {
            return validatePunishmentRequest(
                    connection,
                    request.linkedPunishmentRequestId().orElseThrow(),
                    row
            );
        }
        return Optional.empty();
    }

    private static Optional<ExactSanctionChangeResult> validateAppeal(
            Connection connection,
            UUID appealId,
            ExactSanctionRow row
    ) throws SQLException {
        Optional<ExactSanctionChangeResult> targetFailure = validateAppealTarget(
                connection,
                appealId,
                row
        );
        return targetFailure.isPresent()
                ? targetFailure
                : validateAppealReuse(connection, appealId, row);
    }

    private static Optional<ExactSanctionChangeResult> validateAppealTarget(
            Connection connection,
            UUID appealId,
            ExactSanctionRow row
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT punishment_id, case_id, state
                FROM website_appeal_requests
                WHERE appeal_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return rejected("APPEAL_NOT_FOUND", "The linked appeal does not exist");
                }
                UUID punishmentId = UuidBytes.fromBytes(result.getBytes("punishment_id"));
                String caseId = result.getString(CASE_ID_COLUMN);
                if (!punishmentId.equals(row.sanctionId()) || !caseId.equals(row.caseId().value())) {
                    return rejected(
                            "APPEAL_TARGET_MISMATCH",
                            "The linked appeal does not belong to this sanction and case"
                    );
                }
                return APPLIED_STATE.equals(result.getString("state"))
                        ? Optional.empty()
                        : rejected("APPEAL_NOT_ACCEPTED", "The linked appeal has not been accepted");
            }
        }
    }

    private static Optional<ExactSanctionChangeResult> validateAppealReuse(
            Connection connection,
            UUID appealId,
            ExactSanctionRow row
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_id
                FROM sanction_events
                WHERE linked_appeal_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(new ExactSanctionChangeResult.NoChange(
                                "APPEAL_ALREADY_LINKED",
                                "The appeal is already linked to a sanction reversal",
                                row.caseId(),
                                row.sanctionId(),
                                row.status(),
                                row.expiration()
                        ))
                        : Optional.empty();
            }
        }
    }

    private static Optional<ExactSanctionChangeResult> validatePunishmentRequest(
            Connection connection,
            UUID requestId,
            ExactSanctionRow row
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT target_id, resulting_case_id, status
                FROM punishment_requests
                WHERE request_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(requestId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return rejected(
                            "PUNISHMENT_REQUEST_NOT_FOUND",
                            "The linked punishment request does not exist"
                    );
                }
                return validatePunishmentRequestRow(result, row);
            }
        }
    }

    private static Optional<ExactSanctionChangeResult> validatePunishmentRequestRow(
            ResultSet result,
            ExactSanctionRow row
    ) throws SQLException {
        UUID targetId = UuidBytes.fromBytes(result.getBytes("target_id"));
        String caseId = result.getString("resulting_case_id");
        if (!targetId.equals(row.subjectId()) || caseId == null
                || !caseId.equals(row.caseId().value())) {
            return rejected(
                    "PUNISHMENT_REQUEST_TARGET_MISMATCH",
                    "The linked punishment request does not belong to this player and case"
            );
        }
        String status = result.getString("status");
        return "APPROVED".equals(status) || "FULFILLED_EXTERNALLY".equals(status)
                ? Optional.empty()
                : rejected(
                        "PUNISHMENT_REQUEST_NOT_RESOLVED",
                        "The linked punishment request is not resolved"
                );
    }

    private static Optional<ExactSanctionChangeResult> rejected(String code, String message) {
        return Optional.of(new ExactSanctionChangeResult.Rejected(code, message));
    }
}
