package net.enthusia.staff.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.sanction.SanctionLength;

final class PolicyV2JsonCodec {
    private final JsonMapper json = JsonMapper.builder()
            .addModule(new Jdk8Module())
            .addModule(new JavaTimeModule())
            .addMixIn(IncidentAttributeValue.class, IncidentAttributeValueMixin.class)
            .addMixIn(PolicyAction.class, PolicyActionMixin.class)
            .addMixIn(SanctionLength.class, SanctionLengthMixin.class)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to encode Policy v2 JSON", exception);
        }
    }

    <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to decode Policy v2 JSON", exception);
        }
    }

    <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to decode Policy v2 JSON", exception);
        }
    }

    JsonNode readTree(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ModerationPersistenceException("Unable to decode Policy v2 JSON tree", exception);
        }
    }

    String hash(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "valueType")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = IncidentAttributeValue.BooleanValue.class, name = "BOOLEAN"),
        @JsonSubTypes.Type(value = IncidentAttributeValue.IntegerValue.class, name = "INTEGER"),
        @JsonSubTypes.Type(value = IncidentAttributeValue.EnumValue.class, name = "ENUM"),
        @JsonSubTypes.Type(value = IncidentAttributeValue.TextValue.class, name = "TEXT")
    })
    private interface IncidentAttributeValueMixin {
    }

    private abstract static class SanctionLengthMixin {
        @JsonIgnore
        abstract boolean isInstant();

        @JsonIgnore
        abstract boolean isPermanent();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "actionType")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PolicyAction.Exact.class, name = "EXACT"),
        @JsonSubTypes.Type(value = PolicyAction.Bounded.class, name = "BOUNDED"),
        @JsonSubTypes.Type(value = PolicyAction.RemedyOnly.class, name = "REMEDY_ONLY"),
        @JsonSubTypes.Type(value = PolicyAction.RequiresReview.class, name = "REQUIRES_REVIEW")
    })
    private interface PolicyActionMixin {
    }
}
