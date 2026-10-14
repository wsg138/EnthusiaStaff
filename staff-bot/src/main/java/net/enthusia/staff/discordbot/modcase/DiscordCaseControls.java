package net.enthusia.staff.discordbot.modcase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.domain.modcase.CaseNotice;

/** Scoped, authenticated opaque controls. The click still needs fresh staff authorization. */
public final class DiscordCaseControls {
    private static final String PREFIX = "amc:";
    private final byte[] secret;

    public record Parsed(CaseNotice.Action action, CaseNotice.Origin origin,
                         UUID caseId, long revision) {
    }

    public DiscordCaseControls(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("Discord control key must be at least 256 bits");
        }
        this.secret = secret.clone();
    }

    public String encode(CaseNotice notice, CaseNotice.Action action) {
        Objects.requireNonNull(notice);
        Objects.requireNonNull(action);
        String body = body(action, notice.origin(), notice.caseId(), notice.revision());
        String signed = PREFIX + body + ":" + mac(body, notice.scope());
        if (signed.length() > 100) {
            throw new IllegalArgumentException("case revision exceeds Discord component limit");
        }
        return signed;
    }

    public Parsed parse(String customId, String trustedScope) {
        if (customId == null || !customId.startsWith(PREFIX) || trustedScope == null) {
            throw new IllegalArgumentException("invalid case control");
        }
        String[] parts = customId.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 5 || customId.length() > 100) {
            throw new IllegalArgumentException("malformed case control");
        }
        try {
            CaseNotice.Action action = CaseNotice.Action.valueOf(parts[0]);
            CaseNotice.Origin origin = CaseNotice.Origin.valueOf(parts[1]);
            UUID id = UUID.fromString(parts[2]);
            long revision = Long.parseLong(parts[3]);
            if (revision < 1 || !parts[2].equals(id.toString())) {
                throw new IllegalArgumentException("noncanonical case control");
            }
            String body = body(action, origin, id, revision);
            byte[] expected = mac(body, trustedScope).getBytes(StandardCharsets.US_ASCII);
            byte[] received = parts[4].getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, received)) {
                throw new IllegalArgumentException("invalid case control signature or scope");
            }
            return new Parsed(action, origin, id, revision);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid case control", error);
        }
    }

    private static String body(CaseNotice.Action action, CaseNotice.Origin origin,
                               UUID id, long revision) {
        return action + ":" + origin + ":" + id + ":" + revision;
    }

    private String mac(String body, String scope) {
        try {
            Mac signer = Mac.getInstance("HmacSHA256");
            signer.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] signed = signer.doFinal((body + ":" + scope).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signed, 0, 12);
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("cannot sign case control", error);
        }
    }
}
