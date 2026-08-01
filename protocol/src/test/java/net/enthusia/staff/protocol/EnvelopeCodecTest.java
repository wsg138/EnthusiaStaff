package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvelopeCodecTest {
    private final EnvelopeCodec codec = new EnvelopeCodec();

    @Test
    void roundTripsUnicodePayloadsWithoutChangingTheEnvelope() {
        ProtocolEnvelope envelope = envelope("{\"message\":\"héllo 😀\"}");

        assertEquals(envelope, codec.decode(codec.encode(envelope)));
    }

    @Test
    void rejectsNullEmptyAndOversizedFramesBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[EnvelopeCodec.MAX_FRAME_BYTES + 1]));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("{not-json".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void rejectsUnknownJsonProperties() {
        String base = validJson();
        String json = base.substring(0, base.length() - 1) + ",\"unexpected\":true}";

        assertThrows(IllegalArgumentException.class, () -> codec.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsFramesMissingRequiredRecordFields() {
        String json = validJson().replace("\"serverId\":\"hub\",", "");

        assertThrows(IllegalArgumentException.class, () -> codec.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsDecodedFramesWithBlankValidatedFields() {
        String json = validJson().replace("\"serverId\":\"hub\"", "\"serverId\":\"   \"");

        assertThrows(IllegalArgumentException.class, () -> codec.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEncodedFramesThatExceedTheWireLimitEvenWhenFieldLimitsPass() {
        String payload = "😀".repeat(500_000);
        ProtocolEnvelope envelope = envelope(payload);

        assertEquals(1_000_000, envelope.payloadJson().length());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(envelope));
    }

    @Test
    void maximumFrameSizeIsExactlyOneMebibyte() {
        assertEquals(1_048_576, EnvelopeCodec.MAX_FRAME_BYTES);
    }

    private static ProtocolEnvelope envelope(String payload) {
        return new ProtocolEnvelope(
                1,
                UUID.fromString("53fa6153-2ad8-42ba-a4e9-8fb10d630f08"),
                "hub",
                "PUNISHMENT_CREATED",
                1_785_000_000_000L,
                "nonce",
                payload,
                "00".repeat(32)
        );
    }

    private static String validJson() {
        return "{\"protocolVersion\":1,"
                + "\"messageId\":\"53fa6153-2ad8-42ba-a4e9-8fb10d630f08\","
                + "\"serverId\":\"hub\","
                + "\"messageType\":\"PUNISHMENT_CREATED\","
                + "\"timestampEpochMillis\":1785000000000,"
                + "\"nonce\":\"nonce\","
                + "\"payloadJson\":\"{}\","
                + "\"mac\":\"0000000000000000000000000000000000000000000000000000000000000000\"}";
    }
}
