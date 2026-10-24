package net.enthusia.staff.discordbot.modcase.receiver;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact API #151 ACK allowlist; never expose a persistence/internal record. */
public record CaseAck(String status, String operationId, String eventType,
                      String caseId, long revision, String originPlatform, String scopeId) {
    public Map<String, Object> toWire() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", status);
        fields.put("operation_id", operationId);
        fields.put("event_type", eventType);
        fields.put("case_id", caseId);
        fields.put("revision", revision);
        fields.put("origin_platform", originPlatform);
        fields.put("scope_id", scopeId);
        return Map.copyOf(fields);
    }
}
