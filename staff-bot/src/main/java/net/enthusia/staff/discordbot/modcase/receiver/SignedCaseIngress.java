package net.enthusia.staff.discordbot.modcase.receiver;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * OFFLINE ONLY: a fail-closed, domain-separated HMAC boundary for canonical API
 * event ingress. No network route or staff listener registers this class.
 * The configured secret authenticates a SERVICE, never a staff actor.
 */
public final class SignedCaseIngress {
    private static final String DOMAIN = "enthusia.case.ingress.v1";
    private static final String METHOD = "POST";
    private static final String ENDPOINT = "/v1/ai-case-events";
    private static final int MAX_WIRE_BYTES = 8192;
    private static final long SKEW_SECONDS = 30;
    private static final Pattern MAC_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final CaseReceiver receiver;
    private final String origin;
    private final String scope;
    private final long guildId;
    private final byte[] secret;
    private final Clock clock;

    public SignedCaseIngress(ReceiverJournal journal, String origin, String scope,
                             long guildId, byte[] secret, Clock clock) {
        this.receiver = new CaseReceiver(journal, origin, scope, guildId);
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("dedicated ingress secret must be at least 32 bytes");
        }
        this.origin = origin;
        this.scope = scope;
        this.guildId = guildId;
        this.secret = Arrays.copyOf(secret, secret.length);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Inactive HTTP boundary: verify exact method, route, and strict UTF-8 bytes
     * before forwarding to the cryptographic envelope validator.
     * No HTTP handler or route registers this class.
     */
    public Optional<CaseAck> receiveRaw(String method, String endpoint,
                                        String claimedOrigin, String claimedScope,
                                        long issuedAtSeconds, String signatureHex,
                                        byte[] rawBody) {
        if (!METHOD.equals(method) || !ENDPOINT.equals(endpoint)
                || rawBody == null || rawBody.length > MAX_WIRE_BYTES) {
            return Optional.empty();
        }
        try {
            String wire = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBody)).toString();
            return receive(claimedOrigin, claimedScope, issuedAtSeconds, signatureHex, wire);
        } catch (CharacterCodingException exception) {
            return Optional.empty();
        }
    }

    /**
     * Accept a signed envelope only after verifying service origin, scope, age,
     * body and MAC. The canonical journal handles exact event replay safely.
     *
     * @return empty on any invalid proof, malformed body or failed journal write
     */
    public Optional<CaseAck> receive(String claimedOrigin, String claimedScope,
                                     long issuedAtSeconds, String signatureHex,
                                     String canonicalWire) {
        if (!origin.equals(claimedOrigin) || !scope.equals(claimedScope)
                || signatureHex == null || !MAC_PATTERN.matcher(signatureHex).matches()
                || canonicalWire == null) {
            return Optional.empty();
        }
        byte[] body = canonicalWire.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_WIRE_BYTES || !fresh(issuedAtSeconds)) {
            return Optional.empty();
        }
        if (!validMac(issuedAtSeconds, body, signatureHex)) {
            return Optional.empty();
        }
        return receiver.receive(new CaseReceiver.Peer(true, origin, scope, guildId),
                canonicalWire);
    }

    private boolean validMac(long issuedAtSeconds, byte[] body, String signatureHex) {
        try {
            return MessageDigest.isEqual(expectedMac(issuedAtSeconds, body),
                    HexFormat.of().parseHex(signatureHex));
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    private boolean fresh(long issuedAtSeconds) {
        long now = clock.instant().getEpochSecond();
        return issuedAtSeconds > 0 && issuedAtSeconds >= now - SKEW_SECONDS
                && issuedAtSeconds <= now + SKEW_SECONDS;
    }

    private byte[] expectedMac(long issuedAtSeconds, byte[] body)
            throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
        String transcript = DOMAIN + "\n" + METHOD + "\n" + ENDPOINT + "\n"
                + origin + "\n" + scope + "\n" + guildId + "\n"
                + issuedAtSeconds + "\n" + HexFormat.of().formatHex(digest);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(transcript.getBytes(StandardCharsets.UTF_8));
    }
}
