package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.SanctionSpec;

final class JdbcPunishmentRecommendationCodec {
    private final PunishmentDraftSanctionCodec sanctions;

    JdbcPunishmentRecommendationCodec(ObjectMapper json) {
        this.sanctions = new PunishmentDraftSanctionCodec(json);
    }

    String write(List<SanctionSpec> recommendation) throws JsonProcessingException {
        return sanctions.encode(recommendation);
    }

    Optional<List<SanctionSpec>> read(String serialized) {
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(sanctions.decode(serialized));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read stored punishment recommendation", exception);
        }
    }
}
