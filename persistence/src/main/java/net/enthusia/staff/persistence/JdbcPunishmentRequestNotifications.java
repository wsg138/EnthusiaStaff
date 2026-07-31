package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntentKey;
import net.enthusia.staff.domain.application.PunishmentRequestAlertOccurrence;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;

/** Persists all durable alert and Discord intents inside an existing lifecycle transaction. */
final class JdbcPunishmentRequestNotifications {
    private static final int ALERT_SCHEMA_VERSION = 2;
    private static final Duration TERMINAL_RETENTION = Duration.ofDays(30);

    private final ObjectMapper json;
    private final JdbcPunishmentRequestAlertWriter alerts;

    JdbcPunishmentRequestNotifications(
            ObjectMapper json,
            JdbcPunishmentRequestAlertWriter alerts
    ) {
        if (json == null || alerts == null) {
            throw new IllegalArgumentException("json mapper and alert writer must be present");
        }
        this.json = json;
        this.alerts = alerts;
    }

    void submitted(Connection connection, PunishmentApprovalRequest request) throws SQLException {
        PunishmentRequestAlertOccurrence occurrence =
                PunishmentRequestAlertOccurrence.forRevision(request.revision());
        insertDirect(connection, request, PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                occurrence, request.proposal().requester().id(), request.createdAt(), request.expiresAt());
        insertReviewerWork(connection, request, occurrence);
        insertOperational(connection, request, PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                occurrence, request.createdAt(), request.expiresAt());
        insertDiscord(connection, request, "submitted:" + request.revision(),
                "PUNISHMENT_REQUEST_SUBMITTED", occurrence, null, null, request.createdAt());
    }

    void claimed(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID reviewerId,
            long fenceToken,
            Instant now
    ) throws SQLException {
        PunishmentRequestAlertOccurrence occurrence =
                PunishmentRequestAlertOccurrence.forClaim(fenceToken, reviewerId);
        insertDirect(connection, request, PunishmentRequestLifecycleEventType.REQUEST_CLAIMED,
                occurrence, request.proposal().requester().id(), now, request.expiresAt());
        insertOperational(connection, request, PunishmentRequestLifecycleEventType.REQUEST_CLAIMED,
                occurrence, now, request.expiresAt());
        insertDiscord(connection, request, "claimed:fence:" + fenceToken,
                "PUNISHMENT_REQUEST_CLAIMED", occurrence, reviewerId, null, now);
    }

    void approved(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID approverId,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        terminal(connection, request, PunishmentRequestLifecycleEventType.REQUEST_APPROVED,
                approverId, caseId, now);
    }

    void denied(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID approverId,
            Instant now
    ) throws SQLException {
        terminal(connection, request, PunishmentRequestLifecycleEventType.REQUEST_DENIED,
                approverId, null, now);
    }

    void expired(
            Connection connection,
            PunishmentApprovalRequest request,
            Instant now
    ) throws SQLException {
        terminal(connection, request, PunishmentRequestLifecycleEventType.REQUEST_EXPIRED,
                null, null, now);
    }

    void fulfilledExternally(
            Connection connection,
            PunishmentApprovalRequest request,
            UUID actorId,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        terminal(connection, request, PunishmentRequestLifecycleEventType.REQUEST_EXTERNALLY_FULFILLED,
                actorId, caseId, now);
    }

    private void terminal(
            Connection connection,
            PunishmentApprovalRequest request,
            PunishmentRequestLifecycleEventType eventType,
            UUID actorId,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        PunishmentRequestAlertOccurrence occurrence = actorId == null
                ? PunishmentRequestAlertOccurrence.forRevision(request.revision())
                : PunishmentRequestAlertOccurrence.forRevision(request.revision(), actorId);
        Instant expiresAt = now.plus(TERMINAL_RETENTION);
        insertDirect(connection, request, eventType, occurrence,
                request.proposal().requester().id(), now, expiresAt);
        insertOperational(connection, request, eventType, occurrence, now, expiresAt);
        insertDiscord(connection, request,
                eventType.name().toLowerCase(Locale.ROOT) + ':' + request.revision(),
                discordEventType(eventType), occurrence, actorId, caseId, now);
    }

    private void insertReviewerWork(
            Connection connection,
            PunishmentApprovalRequest request,
            PunishmentRequestAlertOccurrence occurrence
    ) throws SQLException {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                UUID.randomUUID(),
                "pending",
                request.requestId(),
                request.revision(),
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                occurrence,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                null,
                request.proposal().requester().id(),
                approvalMinimum(request.proposal().requiredRank()),
                request.proposal().visibility(),
                ALERT_SCHEMA_VERSION,
                request.createdAt(),
                request.expiresAt()
        );
        alerts.insertOrReplay(connection, finalized(draft));
    }

    private void insertDirect(
            Connection connection,
            PunishmentApprovalRequest request,
            PunishmentRequestLifecycleEventType eventType,
            PunishmentRequestAlertOccurrence occurrence,
            UUID recipientId,
            Instant createdAt,
            Instant expiresAt
    ) throws SQLException {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                UUID.randomUUID(),
                "pending",
                request.requestId(),
                request.revision(),
                eventType,
                occurrence,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                recipientId,
                null,
                null,
                CaseVisibility.PRIVATE,
                ALERT_SCHEMA_VERSION,
                createdAt,
                expiresAt
        );
        alerts.insertOrReplay(connection, finalized(draft));
    }

    private void insertOperational(
            Connection connection,
            PunishmentApprovalRequest request,
            PunishmentRequestLifecycleEventType eventType,
            PunishmentRequestAlertOccurrence occurrence,
            Instant createdAt,
            Instant expiresAt
    ) throws SQLException {
        PunishmentRequestAlertIntent draft = new PunishmentRequestAlertIntent(
                UUID.randomUUID(),
                "pending",
                request.requestId(),
                request.revision(),
                eventType,
                occurrence,
                PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                null,
                null,
                null,
                CaseVisibility.PRIVATE,
                ALERT_SCHEMA_VERSION,
                createdAt,
                expiresAt
        );
        alerts.insertOrReplay(connection, finalized(draft));
    }

    private void insertDiscord(
            Connection connection,
            PunishmentApprovalRequest request,
            String occurrenceKey,
            String eventType,
            PunishmentRequestAlertOccurrence occurrence,
            UUID actorId,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", ALERT_SCHEMA_VERSION);
        payload.put("requestId", request.requestId().toString());
        payload.put("requestRevision", request.revision());
        payload.put("requesterId", request.proposal().requester().id().toString());
        payload.put("targetId", request.proposal().targetId().toString());
        payload.put("reasonId", request.proposal().reasonId());
        payload.put("requiredRank", approvalMinimum(request.proposal().requiredRank()).name());
        payload.put("visibility", request.proposal().visibility().name());
        payload.put("occurrenceKey", occurrence.key());
        if (actorId != null) {
            payload.put("actorId", actorId.toString());
        }
        if (caseId != null) {
            payload.put("caseId", caseId.value());
        }
        String serialized;
        try {
            serialized = json.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Unable to serialize punishment request notification", exception);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_outbox(
                    message_id, idempotency_key, destination, event_type,
                    payload_json, available_at, created_at
                ) VALUES (?, ?, 'punishments', ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, "punishment-request:" + request.requestId() + ':' + occurrenceKey);
            statement.setString(3, eventType);
            statement.setString(4, serialized);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Punishment request Discord intent was not inserted"
            );
        }
    }

    private static PunishmentRequestAlertIntent finalized(PunishmentRequestAlertIntent draft) {
        return new PunishmentRequestAlertIntent(
                draft.alertId(),
                PunishmentRequestAlertIntentKey.forIntent(draft),
                draft.requestId(),
                draft.requestRevision(),
                draft.eventType(),
                draft.occurrence(),
                draft.audience(),
                draft.recipientId(),
                draft.excludedRecipientId(),
                draft.minimumRank(),
                draft.visibility(),
                draft.schemaVersion(),
                draft.createdAt(),
                draft.expiresAt()
        );
    }

    private static StaffRank approvalMinimum(StaffRank requiredRank) {
        return switch (requiredRank) {
            case ADMIN, FOUNDER -> requiredRank;
            case HELPER, MOD -> StaffRank.MOD;
            case DEVELOPER, SYSTEM -> throw new IllegalArgumentException(
                    "developer and system ranks cannot be punishment request approval paths");
        };
    }

    private static String discordEventType(PunishmentRequestLifecycleEventType eventType) {
        return switch (eventType) {
            case REQUEST_SUBMITTED -> "PUNISHMENT_REQUEST_SUBMITTED";
            case REQUEST_CLAIMED -> "PUNISHMENT_REQUEST_CLAIMED";
            case REQUEST_APPROVED -> "PUNISHMENT_REQUEST_APPROVED";
            case REQUEST_DENIED -> "PUNISHMENT_REQUEST_DENIED";
            case REQUEST_EXPIRED -> "PUNISHMENT_REQUEST_EXPIRED";
            case REQUEST_EXTERNALLY_FULFILLED -> "PUNISHMENT_REQUEST_FULFILLED_EXTERNALLY";
        };
    }
}
