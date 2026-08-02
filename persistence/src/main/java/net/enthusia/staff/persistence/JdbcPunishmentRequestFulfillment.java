package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentMatchKey;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;

final class JdbcPunishmentRequestFulfillment {
    private JdbcPunishmentRequestFulfillment() {
    }

    static List<PunishmentApprovalRequest> apply(
            Connection connection,
            PunishmentPlan plan,
            CaseId caseId,
            Instant now,
            UUID excludedRequestId,
            JdbcPunishmentRequestRepository repository,
            JdbcPunishmentRequestEvents events,
            JdbcPunishmentRequestNotifications notifications,
            JdbcPunishmentRequestAlertWriter alertWriter
    ) throws SQLException {
        PunishmentMatchKey matchKey = PunishmentMatchKey.of(
                plan.targetId(),
                plan.reasonId(),
                plan.sanctions()
        );
        List<PunishmentApprovalRequest> matches = repository.matchingPending(
                connection,
                matchKey,
                now,
                excludedRequestId
        );
        java.util.ArrayList<PunishmentApprovalRequest> transitioned = new java.util.ArrayList<>();
        for (PunishmentApprovalRequest match : matches) {
            PunishmentApprovalRequest resolved = repository.resolve(
                    connection,
                    match,
                    PunishmentRequestStatus.FULFILLED_EXTERNALLY,
                    plan.actor().id(),
                    "Exact matching punishment was applied independently",
                    caseId,
                    now
            );
            events.fulfilledExternally(connection, resolved, plan.actor().id(), caseId, now);
            alertWriter.closeActiveReviewerWork(
                    connection,
                    resolved.requestId(),
                    "REQUEST_FULFILLED_EXTERNALLY",
                    now
            );
            notifications.fulfilledExternally(
                    connection,
                    resolved,
                    plan.actor().id(),
                    caseId,
                    now
            );
            deleteLease(connection, resolved.requestId());
            transitioned.add(resolved);
        }
        return List.copyOf(transitioned);
    }

    private static void deleteLease(Connection connection, UUID requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM operation_leases WHERE resource_key = ?")) {
            statement.setString(1, resourceKey(requestId));
            statement.executeUpdate();
        }
    }

    static String resourceKey(UUID requestId) {
        return "punishment-request:" + requestId;
    }
}
