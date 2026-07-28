package net.enthusia.staff.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public final class EnvelopeCodec {
    public static final int MAX_FRAME_BYTES = 1_048_576;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public byte[] encode(ProtocolEnvelope envelope) {
        try {
            byte[] encoded = mapper.writeValueAsBytes(envelope);
            if (encoded.length > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("protocol frame exceeds maximum size");
            }
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("protocol envelope cannot be encoded", exception);
        }
    }

    public ProtocolEnvelope decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("invalid protocol frame length");
        }
        try {
            return mapper.readValue(encoded, ProtocolEnvelope.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid protocol frame", exception);
        }
    }
}
