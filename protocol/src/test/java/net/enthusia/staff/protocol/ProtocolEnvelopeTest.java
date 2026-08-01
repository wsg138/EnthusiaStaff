package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolEnvelopeTest {
    private static final UUID MESSAGE_ID = UUID.fromString("53fa6153-2ad8-42ba-a4e9-8fb10d630f08");
    private static final String HUB = "hub";
    private static final String TYPE = "TYPE";
    private static final String NONCE = "nonce";

    @Test
    void trimsAllValidatedTextFields() {
        ProtocolEnvelope envelope = new ProtocolEnvelope(
                1,
                MESSAGE_ID,
                " hub ",
                " TYPE ",
                123L,
                " nonce ",
                " {} ",
                " aa "
        );

        assertEquals(HUB, envelope.serverId());
        assertEquals(TYPE, envelope.messageType());
        assertEquals(NONCE, envelope.nonce());
        assertEquals("{}", envelope.payloadJson());
        assertEquals("aa", envelope.mac());
    }

    @Test
    void rejectsInvalidVersionAndMissingMessageId() {
        assertThrows(IllegalArgumentException.class, () -> envelope(0, MESSAGE_ID, HUB, TYPE, NONCE, "{}", "aa"));
        assertThrows(IllegalArgumentException.class, () -> envelope(1, null, HUB, TYPE, NONCE, "{}", "aa"));
    }

    @Test
    void rejectsBlankRequiredTextFields() {
        assertThrows(IllegalArgumentException.class, () -> envelope(1, MESSAGE_ID, " ", TYPE, NONCE, "{}", "aa"));
        assertThrows(IllegalArgumentException.class, () -> envelope(1, MESSAGE_ID, HUB, " ", NONCE, "{}", "aa"));
        assertThrows(IllegalArgumentException.class, () -> envelope(1, MESSAGE_ID, HUB, TYPE, " ", "{}", "aa"));
        assertThrows(IllegalArgumentException.class, () -> envelope(1, MESSAGE_ID, HUB, TYPE, NONCE, " ", "aa"));
        assertThrows(IllegalArgumentException.class, () -> envelope(1, MESSAGE_ID, HUB, TYPE, NONCE, "{}", " "));
    }

    @Test
    void acceptsExactMaximumFieldLengths() {
        ProtocolEnvelope envelope = envelope(
                1,
                MESSAGE_ID,
                "s".repeat(64),
                "t".repeat(64),
                "n".repeat(96),
                "p".repeat(1_000_000),
                "a".repeat(128)
        );

        assertEquals(64, envelope.serverId().length());
        assertEquals(64, envelope.messageType().length());
        assertEquals(96, envelope.nonce().length());
        assertEquals(1_000_000, envelope.payloadJson().length());
        assertEquals(128, envelope.mac().length());
    }

    @Test
    void rejectsFieldsAboveTheirMaximumLengths() {
        assertThrows(IllegalArgumentException.class, () -> envelope(
                1, MESSAGE_ID, "s".repeat(65), TYPE, NONCE, "{}", "aa"
        ));
        assertThrows(IllegalArgumentException.class, () -> envelope(
                1, MESSAGE_ID, HUB, "t".repeat(65), NONCE, "{}", "aa"
        ));
        assertThrows(IllegalArgumentException.class, () -> envelope(
                1, MESSAGE_ID, HUB, TYPE, "n".repeat(97), "{}", "aa"
        ));
        assertThrows(IllegalArgumentException.class, () -> envelope(
                1, MESSAGE_ID, HUB, TYPE, NONCE, "p".repeat(1_000_001), "aa"
        ));
        assertThrows(IllegalArgumentException.class, () -> envelope(
                1, MESSAGE_ID, HUB, TYPE, NONCE, "{}", "a".repeat(129)
        ));
    }

    @Test
    void unsignedEnvelopeWithMacPreservesEveryUnsignedField() {
        UnsignedEnvelope unsigned = new UnsignedEnvelope(
                3,
                MESSAGE_ID,
                HUB,
                TYPE,
                456L,
                NONCE,
                "{\"ok\":true}"
        );

        ProtocolEnvelope signed = unsigned.withMac("abcd");

        assertEquals(unsigned.protocolVersion(), signed.protocolVersion());
        assertEquals(unsigned.messageId(), signed.messageId());
        assertEquals(unsigned.serverId(), signed.serverId());
        assertEquals(unsigned.messageType(), signed.messageType());
        assertEquals(unsigned.timestampEpochMillis(), signed.timestampEpochMillis());
        assertEquals(unsigned.nonce(), signed.nonce());
        assertEquals(unsigned.payloadJson(), signed.payloadJson());
        assertEquals("abcd", signed.mac());
    }

    private static ProtocolEnvelope envelope(
            int version,
            UUID messageId,
            String serverId,
            String messageType,
            String nonce,
            String payload,
            String mac
    ) {
        return new ProtocolEnvelope(version, messageId, serverId, messageType, 123L, nonce, payload, mac);
    }
}
