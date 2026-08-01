package net.enthusia.staff.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public final class EnvelopeAuthenticator {
    private static final String ALGORITHM = "HmacSHA256";

    private final int protocolVersion;
    private final Clock clock;
    private final Duration permittedAge;
    private final Duration permittedClockSkew;
    private final Map<String, SecretKey> serverKeys;
    private final ReplayGuard replayGuard;

    public EnvelopeAuthenticator(
            int protocolVersion,
            Clock clock,
            Duration permittedAge,
            Duration permittedClockSkew,
            Map<String, SecretKey> serverKeys,
            ReplayGuard replayGuard
    ) {
        validateConfiguration(
                protocolVersion,
                clock,
                permittedAge,
                permittedClockSkew,
                serverKeys,
                replayGuard
        );
        this.protocolVersion = protocolVersion;
        this.clock = clock;
        this.permittedAge = permittedAge;
        this.permittedClockSkew = permittedClockSkew;
        this.serverKeys = Map.copyOf(serverKeys);
        this.replayGuard = replayGuard;
    }

    private static void validateConfiguration(
            int protocolVersion,
            Clock clock,
            Duration permittedAge,
            Duration permittedClockSkew,
            Map<String, SecretKey> serverKeys,
            ReplayGuard replayGuard
    ) {
        if (protocolVersion < 1) {
            throw invalidConfiguration();
        }
        requirePresent(clock, permittedAge, permittedClockSkew, serverKeys, replayGuard);
        if (serverKeys.isEmpty()) {
            throw invalidConfiguration();
        }
        validateTimeBounds(permittedAge, permittedClockSkew);
    }

    private static void requirePresent(Object... values) {
        for (Object value : values) {
            if (value == null) {
                throw invalidConfiguration();
            }
        }
    }

    private static void validateTimeBounds(Duration permittedAge, Duration permittedClockSkew) {
        if (permittedAge.isZero() || permittedAge.isNegative()) {
            throw invalidConfiguration();
        }
        if (permittedClockSkew.isNegative()) {
            throw invalidConfiguration();
        }
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException(
                "authenticator configuration must be present and time bounds must be safe"
        );
    }

    public ProtocolEnvelope sign(UnsignedEnvelope envelope) {
        SecretKey key = serverKeys.get(envelope.serverId());
        if (key == null) {
            throw new IllegalArgumentException("unknown server ID");
        }
        return envelope.withMac(HexFormat.of().formatHex(mac(key, canonical(envelope))));
    }

    public VerificationResult verify(ProtocolEnvelope envelope) {
        SecretKey key = serverKeys.get(envelope.serverId());
        if (key == null) {
            return new VerificationResult(VerificationStatus.UNKNOWN_SERVER);
        }
        if (envelope.protocolVersion() != protocolVersion) {
            return new VerificationResult(VerificationStatus.UNSUPPORTED_VERSION);
        }
        Instant now = clock.instant();
        Instant timestamp = Instant.ofEpochMilli(envelope.timestampEpochMillis());
        if (timestamp.isAfter(now.plus(permittedClockSkew))) {
            return new VerificationResult(VerificationStatus.FUTURE_TIMESTAMP);
        }
        if (timestamp.isBefore(now.minus(permittedAge))) {
            return new VerificationResult(VerificationStatus.EXPIRED);
        }
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(envelope.mac());
        } catch (IllegalArgumentException exception) {
            return new VerificationResult(VerificationStatus.INVALID_MAC);
        }
        byte[] expected = mac(key, canonical(new UnsignedEnvelope(
                envelope.protocolVersion(),
                envelope.messageId(),
                envelope.serverId(),
                envelope.messageType(),
                envelope.timestampEpochMillis(),
                envelope.nonce(),
                envelope.payloadJson()
        )));
        if (!MessageDigest.isEqual(expected, supplied)) {
            return new VerificationResult(VerificationStatus.INVALID_MAC);
        }
        if (!replayGuard.recordIfNew(envelope.serverId(), envelope.nonce(), now)) {
            return new VerificationResult(VerificationStatus.REPLAYED);
        }
        return new VerificationResult(VerificationStatus.ACCEPTED);
    }

    public Set<String> allowedServers() {
        return serverKeys.keySet();
    }

    private static byte[] canonical(UnsignedEnvelope envelope) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(envelope.protocolVersion());
                output.writeLong(envelope.messageId().getMostSignificantBits());
                output.writeLong(envelope.messageId().getLeastSignificantBits());
                writeString(output, envelope.serverId());
                writeString(output, envelope.messageType());
                output.writeLong(envelope.timestampEpochMillis());
                writeString(output, envelope.nonce());
                writeString(output, envelope.payloadJson());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to canonicalize an in-memory envelope", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static byte[] mac(SecretKey key, byte[] value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC provider is unavailable", exception);
        }
    }
}
