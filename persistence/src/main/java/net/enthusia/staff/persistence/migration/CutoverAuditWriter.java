package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.CutoverAssessment;
import net.enthusia.staff.domain.migration.CutoverEvidence;
import net.enthusia.staff.domain.migration.DecisionComparison;
import net.enthusia.staff.domain.migration.FounderOverride;
import net.enthusia.staff.persistence.UuidBytes;

final class CutoverAuditWriter {
    private final ObjectMapper json;

    CutoverAuditWriter(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("cutover audit mapper must be present");
        }
        this.json = json;
    }

    String assessmentJson(
            CutoverEvidence evidence,
            CutoverAssessment assessment,
            Optional<FounderOverride> override
    ) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>(evidenceJson(evidence));
        value.put("allowed", assessment.allowed());
        value.put("founderOverrideUsed", assessment.founderOverrideUsed());
        if (assessment.founderOverrideUsed()) {
            FounderOverride used = override.orElseThrow();
            value.put("founderOverride", Map.of(
                    "actorId", used.actorId().toString(),
                    "warningAcknowledgement", used.warningAcknowledgement(),
                    "reason", used.reason()
            ));
        }
        return serialize(value, "Unable to serialize cutover assessment");
    }

    String blockersJson(List<String> blockers) throws SQLException {
        return serialize(blockers, "Unable to serialize cutover blockers");
    }

    void appendTransition(
            Connection connection,
            UUID actorId,
            OperationalMode previous,
            OperationalMode next,
            String reason,
            String auditType,
            Instant occurredAt
    ) throws SQLException {
        Map<String, Object> payload = Map.of(
                "previousMode", previous.name(),
                "nextMode", next.name(),
                "reason", reason
        );
        insertAudit(
                connection,
                UUID.randomUUID(),
                actorId,
                auditType,
                serialize(payload, "Unable to serialize cutover transition audit"),
                occurredAt
        );
    }

    void appendActivation(
            Connection connection,
            UUID cutoverId,
            UUID actorId,
            CutoverAssessment assessment,
            Optional<FounderOverride> override,
            Instant occurredAt
    ) throws SQLException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("founderOverrideUsed", assessment.founderOverrideUsed());
        payload.put("blockers", assessment.blockers());
        if (assessment.founderOverrideUsed()) {
            FounderOverride used = override.orElseThrow();
            payload.put("founderOverrideWarningAcknowledgement", used.warningAcknowledgement());
            payload.put("founderOverrideReason", used.reason());
        }
        insertAudit(
                connection,
                cutoverId,
                actorId,
                "LITEBANS_CUTOVER_ACTIVATED",
                serialize(payload, "Unable to serialize cutover activation audit"),
                occurredAt
        );
    }

    private void insertAudit(
            Connection connection,
            UUID correlationId,
            UUID actorId,
            String eventType,
            String eventJson,
            Instant occurredAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(event_id, correlation_id, actor_id, event_type,
                    outcome, event_json, occurred_at)
                VALUES (?, ?, ?, ?, 'COMMITTED', ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(correlationId));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setString(4, eventType);
            statement.setString(5, eventJson);
            statement.setTimestamp(6, Timestamp.from(occurredAt));
            MigrationTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "cutover audit insert did not affect exactly one row"
            );
        }
    }

    private String serialize(Object value, String message) throws SQLException {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException(message, exception);
        }
    }

    private static Map<String, Object> evidenceJson(CutoverEvidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("shadowStartedAt", evidence.shadowStartedAt().toString());
        value.put("shadowEndedAt", evidence.shadowEndedAt().toString());
        value.put("assessedAt", evidence.assessedAt().toString());
        value.put("successfulShadowSummaries", evidence.successfulShadowSummaries().stream()
                .map(Instant::toString).toList());
        value.put("countsMatch", evidence.countsMatch());
        value.put("checksumsMatch", evidence.checksumsMatch());
        value.put("activeSanctionsMatch", evidence.activeSanctionsMatch());
        value.put("uuidMappingsMatch", evidence.uuidMappingsMatch());
        value.put("expirationsMatch", evidence.expirationsMatch());
        value.put("loginDecisions", decisionJson(evidence.loginDecisions()));
        value.put("muteDecisions", decisionJson(evidence.muteDecisions()));
        value.put("ipBanDecisions", decisionJson(evidence.ipBanDecisions()));
        value.put("unresolvedOperations", evidence.unresolvedOperations());
        value.put("migrationIdle", evidence.migrationIdle());
        value.put("writesFrozen", evidence.writesFrozen());
        value.put("finalIncrementalImportComplete", evidence.finalIncrementalImportComplete());
        return Map.copyOf(value);
    }

    private static Map<String, Long> decisionJson(DecisionComparison comparison) {
        return Map.of(
                "compared", comparison.compared(),
                "mismatched", comparison.mismatched()
        );
    }
}
