package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class EnvelopeAuthenticatorTest {
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private static final Duration MAX_SKEW = Duration.ofSeconds(30);
    private static final String HUB = "hub";

    @Test
    void rejectsUnsafeOrMissingConfiguration() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        Map<String, SecretKey> keys = Map.of(HUB, key());
        ReplayGuard guard = new ReplayGuard(100, Duration.ofMinutes(6));

        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                0, clock, MAX_AGE, MAX_SKEW, keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, null, MAX_AGE, MAX_SKEW, keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, null, MAX_SKEW, keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, Duration.ZERO, MAX_SKEW, keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, Duration.ofSeconds(-1), MAX_SKEW, keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, MAX_AGE, null, keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, MAX_AGE, Duration.ofSeconds(-1), keys, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, MAX_AGE, MAX_SKEW, null, guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, MAX_AGE, MAX_SKEW, Map.of(), guard
        ));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeAuthenticator(
                1, clock, MAX_AGE, MAX_SKEW, keys, null
        ));
    }

    @Test
    void zeroClockSkewIsAValidStrictConfiguration() {
        EnvelopeAuthenticator authenticator = new EnvelopeAuthenticator(
                1,
                Clock.fixed(NOW, ZoneOffset.UTC),
                MAX_AGE,
                Duration.ZERO,
                Map.of(HUB, key()),
                new ReplayGuard(100, Duration.ofMinutes(6))
        );

        assertTrue(authenticator.verify(authenticator.sign(unsigned(NOW, "{}"))).accepted());
    }

    @Test
    void acceptsValidMessageOnceThenRejectsReplay() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{}"));

        assertTrue(authenticator.verify(signed).accepted());
        assertEquals(VerificationStatus.REPLAYED, authenticator.verify(signed).status());
    }

    @Test
    void acceptsTimestampsExactlyOnTheAgeAndClockSkewBoundaries() {
        EnvelopeAuthenticator authenticator = authenticator();

        ProtocolEnvelope oldestAllowed = authenticator.sign(unsigned(NOW.minus(MAX_AGE), "{}"));
        ProtocolEnvelope newestAllowed = authenticator.sign(unsigned(NOW.plus(MAX_SKEW), "{}"));

        assertTrue(authenticator.verify(oldestAllowed).accepted());
        assertTrue(authenticator.verify(newestAllowed).accepted());
    }

    @Test
    void rejectsExpiredAndFutureMessagesBeyondTheBoundaries() {
        EnvelopeAuthenticator authenticator = authenticator();

        ProtocolEnvelope expired = authenticator.sign(unsigned(NOW.minus(MAX_AGE).minusMillis(1), "{}"));
        ProtocolEnvelope future = authenticator.sign(unsigned(NOW.plus(MAX_SKEW).plusMillis(1), "{}"));

        assertEquals(VerificationStatus.EXPIRED, authenticator.verify(expired).status());
        assertEquals(VerificationStatus.FUTURE_TIMESTAMP, authenticator.verify(future).status());
    }

    @Test
    void rejectsUnknownServersAndUnsupportedProtocolVersionsBeforeMacProcessing() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{}"));
        ProtocolEnvelope unknownServer = new ProtocolEnvelope(
                signed.protocolVersion(),
                signed.messageId(),
                "unknown",
                signed.messageType(),
                signed.timestampEpochMillis(),
                signed.nonce(),
                signed.payloadJson(),
                "not-even-hex"
        );
        ProtocolEnvelope unsupportedVersion = new ProtocolEnvelope(
                2,
                signed.messageId(),
                signed.serverId(),
                signed.messageType(),
                signed.timestampEpochMillis(),
                signed.nonce(),
                signed.payloadJson(),
                "not-even-hex"
        );

        assertEquals(VerificationStatus.UNKNOWN_SERVER, authenticator.verify(unknownServer).status());
        assertEquals(VerificationStatus.UNSUPPORTED_VERSION, authenticator.verify(unsupportedVersion).status());
    }

    @Test
    void rejectsMalformedHexAndIncorrectMacLengths() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{}"));
        ProtocolEnvelope malformed = replaceMac(signed, "not-hex");
        ProtocolEnvelope wrongLength = replaceMac(signed, "00");

        assertEquals(VerificationStatus.INVALID_MAC, authenticator.verify(malformed).status());
        assertEquals(VerificationStatus.INVALID_MAC, authenticator.verify(wrongLength).status());
    }

    @Test
    void rejectsTamperingWithEveryCanonicalMessageField() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{}"));

        assertInvalidMac(authenticator, new ProtocolEnvelope(
                signed.protocolVersion(), UUID.randomUUID(), signed.serverId(), signed.messageType(),
                signed.timestampEpochMillis(), signed.nonce(), signed.payloadJson(), signed.mac()
        ));
        assertInvalidMac(authenticator, new ProtocolEnvelope(
                signed.protocolVersion(), signed.messageId(), signed.serverId(), "OTHER_TYPE",
                signed.timestampEpochMillis(), signed.nonce(), signed.payloadJson(), signed.mac()
        ));
        assertInvalidMac(authenticator, new ProtocolEnvelope(
                signed.protocolVersion(), signed.messageId(), signed.serverId(), signed.messageType(),
                NOW.minusSeconds(1).toEpochMilli(), signed.nonce(), signed.payloadJson(), signed.mac()
        ));
        assertInvalidMac(authenticator, new ProtocolEnvelope(
                signed.protocolVersion(), signed.messageId(), signed.serverId(), signed.messageType(),
                signed.timestampEpochMillis(), "different-nonce", signed.payloadJson(), signed.mac()
        ));
        assertInvalidMac(authenticator, new ProtocolEnvelope(
                signed.protocolVersion(), signed.messageId(), signed.serverId(), signed.messageType(),
                signed.timestampEpochMillis(), signed.nonce(), "{\"admin\":true}", signed.mac()
        ));

        assertTrue(authenticator.verify(signed).accepted());
    }

    @Test
    void signRejectsAnUnknownServerId() {
        EnvelopeAuthenticator authenticator = authenticator();
        UnsignedEnvelope unknown = new UnsignedEnvelope(
                1,
                UUID.randomUUID(),
                "unknown",
                "PUNISHMENT_CREATED",
                NOW.toEpochMilli(),
                UUID.randomUUID().toString(),
                "{}"
        );

        assertThrows(IllegalArgumentException.class, () -> authenticator.sign(unknown));
    }

    @Test
    void allowedServerViewIsImmutable() {
        EnvelopeAuthenticator authenticator = authenticator();

        assertEquals(java.util.Set.of(HUB), authenticator.allowedServers());
        assertThrows(UnsupportedOperationException.class, () -> authenticator.allowedServers().clear());
    }

    @Test
    void codecRoundTripsWithinBound() {
        EnvelopeAuthenticator authenticator = authenticator();
        ProtocolEnvelope signed = authenticator.sign(unsigned(NOW, "{\"caseId\":\"ABC\"}"));
        EnvelopeCodec codec = new EnvelopeCodec();
        assertEquals(signed, codec.decode(codec.encode(signed)));
    }

    private static void assertInvalidMac(EnvelopeAuthenticator authenticator, ProtocolEnvelope envelope) {
        assertEquals(VerificationStatus.INVALID_MAC, authenticator.verify(envelope).status());
    }

    private static ProtocolEnvelope replaceMac(ProtocolEnvelope envelope, String mac) {
        return new ProtocolEnvelope(
                envelope.protocolVersion(),
                envelope.messageId(),
                envelope.serverId(),
                envelope.messageType(),
                envelope.timestampEpochMillis(),
                envelope.nonce(),
                envelope.payloadJson(),
                mac
        );
    }

    private static EnvelopeAuthenticator authenticator() {
        return new EnvelopeAuthenticator(
                1,
                Clock.fixed(NOW, ZoneOffset.UTC),
                MAX_AGE,
                MAX_SKEW,
                Map.of(HUB, key()),
                new ReplayGuard(100, Duration.ofMinutes(6))
        );
    }

    private static SecretKey key() {
        return new SecretKeySpec(new byte[32], "HmacSHA256");
    }

    private static UnsignedEnvelope unsigned(Instant timestamp, String payload) {
        return new UnsignedEnvelope(
                1,
                UUID.randomUUID(),
                HUB,
                "PUNISHMENT_CREATED",
                timestamp.toEpochMilli(),
                UUID.randomUUID().toString(),
                payload
        );
    }
}
