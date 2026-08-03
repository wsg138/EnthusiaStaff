package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionSpec;

final class JdbcPunishmentRecommendationCodec {
    private static final TypeReference<List<SanctionSpec>> SANCTIONS = new TypeReference<>() { };

    private final ObjectMapper json;

    JdbcPunishmentRecommendationCodec(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("json must be present");
        }
        this.json = json;
    }

    String write(List<SanctionSpec> sanctions) throws JsonProcessingException {
        if (sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("recommended sanctions must be present");
        }
        return json.writeValueAsString(List.copyOf(sanctions));
    }

    Optional<List<SanctionSpec>> read(String serialized) {
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            List<SanctionSpec> sanctions = List.copyOf(json.readValue(serialized, SANCTIONS));
            if (sanctions.isEmpty()) {
                throw new ModerationPersistenceException("Stored punishment recommendation is empty");
            }
            return Optional.of(sanctions);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read stored punishment recommendation", exception);
        }
    }
}
