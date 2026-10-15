package net.enthusia.staff.paper.modcase;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;

/**
 * Inactive Paper-side HMAC signer. Only the package-local, authorized policy adapter
 * can invoke issuance; a caller-supplied staff_actor_id is never an authentication credential.
 * The independent key belongs to Paper and the API, NEVER StaffBot.
 */
public final class PaperCaseActorProofIssuer {
    private static final String ALGORITHM = "HmacSHA256";
    private static final long LIFETIME_SECONDS = 30;
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9_:/.-]{1,160}");
    private static final Pattern CLIENT = Pattern.compile("[A-Za-z0-9_.-]{1,96}");

    public record Proof(long issuedAt, long expiresAt, String signature) { }

    private final String boundServerScope;
    private final String clientId;
    private final byte[] authorityKey;
    private final Clock clock;

    // Package-private: there must be no public unguarded proof-signing entrypoint.
    PaperCaseActorProofIssuer(String boundServerScope, String clientId, byte[] authorityKey, Clock clock) {
        if (boundServerScope == null || !SCOPE.matcher(boundServerScope).matches()
                || clientId == null || !CLIENT.matcher(clientId).matches()
                || authorityKey == null || authorityKey.length < 32 || clock == null) {
            throw new IllegalArgumentException("invalid independent case authority settings");
        }
        this.boundServerScope = boundServerScope;
        this.clientId = clientId;
        this.authorityKey = Arrays.copyOf(authorityKey, authorityKey.length);
        this.clock = clock;
    }

    // Approval has a private constructor in PaperCaseStaffAuthority and is bound to
    // one exact command, staff UUID and server scope. No generic CurrentRoleGate exists.
    Proof issue(AuthorityBackedCaseActions.Command command, PaperCaseStaffAuthority.Approval approval) {
        Objects.requireNonNull(command, "case command");
        if (approval == null || !approval.matches(command)
                || !boundServerScope.equals(command.scope())
                || !("minecraft:" + approval.staffUuid()).equals(command.actor())) {
            throw new SecurityException("authenticated staff actor not authorized for case command");
        }
        long issuedAt = clock.instant().getEpochSecond();
        long expiresAt = Math.addExact(issuedAt, LIFETIME_SECONDS);
        return new Proof(issuedAt, expiresAt, sign(preimage(command, issuedAt, expiresAt)));
    }

    private byte[] preimage(AuthorityBackedCaseActions.Command command, long issuedAt, long expiresAt) {
        String platform = command.origin().name().toLowerCase(java.util.Locale.ROOT);
        String content = """
                {"action":"%s","assertion":"STAFF_ACTION_AUTHORIZED","case_id":"%s","client_id":"%s","command_id":"%s","contract_version":1,"expected_revision":%d,"expires_at":%d,"issued_at":%d,"origin_platform":"%s","scope_id":"%s","staff_actor_id":"%s"}"""
                .formatted(command.action().name(), command.caseId(), clientId,
                        command.commandId(), command.expectedRevision(), expiresAt, issuedAt,
                        platform, boundServerScope, command.actor());
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private String sign(byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(authorityKey, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("independent case signature unavailable", exception);
        }
    }
}
