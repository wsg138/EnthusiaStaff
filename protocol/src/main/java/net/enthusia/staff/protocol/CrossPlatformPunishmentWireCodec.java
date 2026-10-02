package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Strict JSON codec for the private Both-plan boundary. */
public final class CrossPlatformPunishmentWireCodec {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private CrossPlatformPunishmentWireCodec() { }

    public static String encodeRequest(CrossPlatformPunishmentPreparationWire.Request request) {
        return write(request, "request");
    }

    public static CrossPlatformPunishmentPreparationWire.Request decodeRequest(String json) {
        return read(json, CrossPlatformPunishmentPreparationWire.Request.class, "request");
    }

    public static String encodeResponse(CrossPlatformPunishmentPreparationWire.Response response) {
        return write(response, "response");
    }

    public static CrossPlatformPunishmentPreparationWire.Response decodeResponse(String json) {
        return read(json, CrossPlatformPunishmentPreparationWire.Response.class, "response");
    }

    private static String write(Object value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to encode cross-platform " + label, exception);
        }
    }

    private static <T> T read(String json, Class<T> type, String label) {
        if (json == null || json.isBlank()
                || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > CrossPlatformPunishmentPreparationWire.MAX_BODY_BYTES) {
            throw new IllegalArgumentException("cross-platform " + label + " body is invalid");
        }
        try { return JSON.readValue(json, type); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to decode cross-platform " + label, exception);
        }
    }
}
