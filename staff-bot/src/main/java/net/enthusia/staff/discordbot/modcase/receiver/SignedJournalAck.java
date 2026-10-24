package net.enthusia.staff.discordbot.modcase.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Default-OFF journal ACK signer for offline transport-interoperability tests.
 * An ACK proves at most case journal acceptance, NEVER Discord/Paper publication.
 * Production transport and publication receipt verification do not exist here.
 */
public final class SignedJournalAck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DOMAIN = "enthusia.case.journal-ack.v1";
    private static final String METHOD = "POST";
    private static final String ENDPOINT = "/v1/ai-case-events";
    private static final int MAX_BODY_BYTES = 2048;
    private static final Set<String> STATUSES = Set.of("ACCEPTED", "DUPLICATE", "REJECTED");

    public record Proof(byte[] body, long issuedAtSeconds, String signatureHex) {
        public Proof {
            body = Arrays.copyOf(body, body.length);
        }

        @Override
        public byte[] body() {
            return Arrays.copyOf(body, body.length);
        }
    }

    private SignedJournalAck() {
    }

    public static Proof sign(CaseAck ack, String origin, String scope, long guildId,
                             long issuedAtSeconds, byte[] separateResponseKey) {
        validate(ack, origin, scope, guildId, issuedAtSeconds, separateResponseKey);
        try {
            Map<String, Object> sorted = new TreeMap<>(ack.toWire());
            byte[] body = JSON.writeValueAsBytes(sorted);
            if (body.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("ACK exceeds byte cap");
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            String transcript = DOMAIN + "\n" + METHOD + "\n" + ENDPOINT + "\n"
                    + origin + "\n" + scope + "\n" + guildId + "\n"
                    + issuedAtSeconds + "\n" + HexFormat.of().formatHex(digest);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(separateResponseKey, "HmacSHA256"));
            return new Proof(body, issuedAtSeconds, HexFormat.of().formatHex(
                    mac.doFinal(transcript.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException | java.io.IOException error) {
            throw new IllegalStateException("offline ACK signing failed", error);
        }
    }

    private static void validate(CaseAck ack, String origin, String scope, long guildId,
                                 long issuedAtSeconds, byte[] key) {
        if (!validAck(ack, origin, scope) || !validDestination(guildId, issuedAtSeconds, key)) {
            throw new IllegalArgumentException("invalid bound journal acknowledgement");
        }
    }

    private static boolean validAck(CaseAck ack, String origin, String scope) {
        return ack != null && STATUSES.contains(ack.status())
                && origin != null && origin.equals(ack.originPlatform())
                && scope != null && scope.equals(ack.scopeId());
    }

    private static boolean validDestination(long guildId, long issuedAtSeconds, byte[] key) {
        return guildId > 0 && issuedAtSeconds > 0 && key != null && key.length >= 32;
    }

}
