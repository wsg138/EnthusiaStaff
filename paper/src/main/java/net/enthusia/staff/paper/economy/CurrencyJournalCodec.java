package net.enthusia.staff.paper.economy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class CurrencyJournalCodec {
    private final ObjectMapper json;

    CurrencyJournalCodec(ObjectMapper json) {
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    String snapshot(CurrencyAccountState snapshot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("playerId", snapshot.playerId().toString());
        values.put("bankBalance", snapshot.bankBalance());
        values.put("bankRevision", snapshot.bankRevision());
        values.put("inventory", snapshot.inventory());
        values.put("enderChest", snapshot.enderChest());
        values.put("inventoryValue", snapshot.inventoryValue());
        values.put("enderChestValue", snapshot.enderChestValue());
        values.put("authoritativeTotal", snapshot.authoritativeTotal());
        values.put("checksum", snapshot.checksum());
        return write(values);
    }

    CurrencyAccountState snapshot(String body) {
        try {
            JsonNode root = json.readTree(body);
            return new CurrencyAccountState(
                    UUID.fromString(requiredText(root, "playerId")),
                    requiredLong(root, "bankBalance"),
                    requiredLong(root, "bankRevision"),
                    requiredBinary(root, "inventory"),
                    requiredBinary(root, "enderChest"),
                    requiredLong(root, "inventoryValue"),
                    requiredLong(root, "enderChestValue"),
                    requiredLong(root, "authoritativeTotal"),
                    requiredText(root, "checksum")
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored currency snapshot JSON is invalid", exception);
        }
    }

    String plan(CurrencyRemovalPlanState plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("operationId", plan.operationId().toString());
        values.put("playerId", plan.playerId().toString());
        values.put("amount", plan.amount());
        values.put("beforeChecksum", plan.before().checksum());
        values.put("replacementBankBalance", plan.replacementBankBalance());
        values.put("replacementInventory", plan.replacementInventory());
        values.put("replacementEnderChest", plan.replacementEnderChest());
        values.put("expectedFinalTotal", plan.expectedFinalTotal());
        values.put("replacementChecksum", plan.replacementChecksum());
        values.put("sourceOrder", plan.sourceOrder().stream().map(Enum::name).toList());
        return write(values);
    }

    private String write(Map<String, ?> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize exact Currency state", exception);
        }
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value.textValue();
    }

    private static long requiredLong(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0L) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.longValue();
    }

    private static byte[] requiredBinary(JsonNode root, String field) throws IOException {
        JsonNode value = root.path(field);
        if (!value.isBinary() && !value.isTextual()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        byte[] decoded = value.binaryValue();
        if (decoded == null) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return decoded;
    }
}
