package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Strict JSON codec for the explicit D08 Minecraft punishment wire contracts. */
public final class MinecraftPunishmentWireCodec {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private MinecraftPunishmentWireCodec() {
    }

    public static String encodeRequest(MinecraftPunishmentPreparationWire.Request request) {
        return write(request, "request");
    }

    public static MinecraftPunishmentPreparationWire.Request decodeRequest(String json) {
        return read(json, MinecraftPunishmentPreparationWire.Request.class, "request");
    }

    public static String encodeResponse(MinecraftPunishmentPreparationWire.Response response) {
        return write(response, "response");
    }

    public static MinecraftPunishmentPreparationWire.Response decodeResponse(String json) {
        return read(json, MinecraftPunishmentPreparationWire.Response.class, "response");
    }

    public static String encodeCommitRequest(MinecraftPunishmentCommitWire.Request request) {
        return write(request, "commit request");
    }

    public static MinecraftPunishmentCommitWire.Request decodeCommitRequest(String json) {
        return read(json, MinecraftPunishmentCommitWire.Request.class, "commit request");
    }

    public static String encodeCommitResponse(MinecraftPunishmentCommitWire.Response response) {
        return write(response, "commit response");
    }

    public static MinecraftPunishmentCommitWire.Response decodeCommitResponse(String json) {
        return read(json, MinecraftPunishmentCommitWire.Response.class, "commit response");
    }

    private static String write(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must be present");
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to encode Minecraft punishment " + label, exception);
        }
    }

    private static <T> T read(String json, Class<T> type, String label) {
        if (json == null || json.isBlank() || json.length() > MinecraftPunishmentPreparationWire.MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Minecraft punishment " + label + " body is invalid");
        }
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to decode Minecraft punishment " + label, exception);
        }
    }
}
