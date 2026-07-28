package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

final class PunishmentDraftSanctionCodec {
    private final ObjectMapper json;

    PunishmentDraftSanctionCodec(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("json mapper must be present");
        }
        this.json = json;
    }

    String encode(List<SanctionSpec> sanctions) throws JsonProcessingException {
        if (sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("draft sanctions must be present");
        }
        ArrayNode encoded = json.createArrayNode();
        for (SanctionSpec sanction : sanctions) {
            ObjectNode item = encoded.addObject();
            item.put("type", sanction.type().name());
            item.put("kind", sanction.length().kind().name());
            sanction.length().temporary().ifPresent(duration -> item.put("duration", duration.toString()));
        }
        return json.writeValueAsString(encoded);
    }

    List<SanctionSpec> decode(String encoded) throws JsonProcessingException {
        JsonNode root = json.readTree(encoded);
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("draft sanction snapshot must be a non-empty array");
        }
        List<SanctionSpec> sanctions = new ArrayList<>();
        for (JsonNode item : root) {
            String typeValue = requiredText(item, "type");
            String kindValue = requiredText(item, "kind");
            SanctionType type = SanctionType.valueOf(typeValue);
            SanctionLength.Kind kind = SanctionLength.Kind.valueOf(kindValue);
            SanctionLength length = switch (kind) {
                case INSTANT -> SanctionLength.instant();
                case PERMANENT -> SanctionLength.permanent();
                case TEMPORARY -> SanctionLength.temporary(Duration.parse(requiredText(item, "duration")));
            };
            sanctions.add(new SanctionSpec(type, length));
        }
        return List.copyOf(sanctions);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("draft sanction " + field + " must be present");
        }
        return value.textValue();
    }
}
