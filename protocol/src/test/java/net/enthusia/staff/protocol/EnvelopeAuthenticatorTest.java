package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class EnvelopeAuthenticatorTest {
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    @Test
    void acceptsValidMessageOnceThenRejectsReplay() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{}"));

        assertTrue(authenticator.verify(signed).accepted());
        assertEquals(VerificationStatus.REPLAYED, authenticator.verify(signed).status());
    }

    @Test
    void rejectsPayloadTamperingAndExpiredMessages() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{}"));
        ProtocolEnvelope tampered = new ProtocolEnvelope(
                signed.protocolVersion(), signed.messageId(), signed.serverId(), signed.messageType(),
                signed.timestampEpochMillis(), signed.nonce(), "{\"admin\":true}", signed.mac()
        );

        assertEquals(VerificationStatus.INVALID_MAC, authenticator.verify(tampered).status());
        ProtocolEnvelope expired = authenticator.sign(unsigned(NOW.minus(Duration.ofMinutes(6)), "{}"));
        assertEquals(VerificationStatus.EXPIRED, authenticator.verify(expired).status());
    }

    @Test
    void codecRoundTripsWithinBound() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{\"caseId\":\"ABC\"}"));
        EnvelopeCodec codec = new EnvelopeCodec();
        assertEquals(signed, codec.decode(codec.encode(signed)));
    }

    private static EnvelopeAuthenticator authenticator() {
        return new EnvelopeAuthenticator(
                1,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Map.of("hub", new SecretKeySpec(new byte[32], "HmacSHA256")),
                new ReplayGuard(100, Duration.ofMinutes(6))
        );
    }

    private static UnsignedEnvelope unsigned(Instant timestamp, String payload) {
        return new UnsignedEnvelope(
                1,
                UUID.randomUUID(),
                "hub",
                "PUNISHMENT_CREATED",
                timestamp.toEpochMilli(),
                UUID.randomUUID().toString(),
                payload
        );
    }
}
