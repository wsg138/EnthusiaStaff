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
 * Unregistered Paper-side staff authority signer for the inactive API case contract.
 * MUST NOT be instantiated without a genuine current-LuckPerms/on-duty identity gate.
 * Signing keys are only for Paper+API and must never be disclosed to StaffBot.
 */
public final class PaperCaseActorProofIssuer {
    private static final String ALGORITHM = "HmacSHA256";
    private static final long LIFETIME_SECONDS = 30;
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9_:/.-]{1,160}");
    private static final Pattern CLIENT = Pattern.compile("[A-Za-z0-9_.-]{1,96}");
    private static final String ACTOR_PREFIX = "minecraft:";

    @FunctionalInterface
    public interface CurrentRoleGate {
        /** Call only after independently validating Minecraft identity, staff rank and duty. */
        boolean permits(UUID staffUuid, String scopeId, AuthorityBackedCaseActions.Command command);
    }

    public record Proof(long issuedAt, long expiresAt, String signature) { }

    private final String boundServerScope;
    private final String clientId;
    private final byte[] authorityKey;
    private final Clock clock;
    private final CurrentRoleGate gate;

    public PaperCaseActorProofIssuer(
            String boundServerScope, String clientId, byte[] authorityKey,
            Clock clock, CurrentRoleGate gate) {
        if (boundServerScope == null || !SCOPE.matcher(boundServerScope).matches()
                || clientId == null || !CLIENT.matcher(clientId).matches()
                || authorityKey == null || authorityKey.length < 32
                || clock == null || gate == null) {
            throw new IllegalArgumentException("invalid independent case authority settings");
        }
        this.boundServerScope = boundServerScope;
        this.clientId = clientId;
        this.authorityKey = Arrays.copyOf(authorityKey, authorityKey.length);
        this.clock = clock;
        this.gate = gate;
    }

    public Proof issue(AuthorityBackedCaseActions.Command command) {
        Objects.requireNonNull(command, "case command");
        UUID staffId = actorId(command.actor());
        if (!boundServerScope.equals(command.scope())
                || !gate.permits(staffId, boundServerScope, command)) {
            throw new SecurityException("staff actor not authorized for case command");
        }
        long issuedAt = clock.instant().getEpochSecond();
        long expiresAt = Math.addExact(issuedAt, LIFETIME_SECONDS);
        return new Proof(issuedAt, expiresAt, sign(preimage(command, issuedAt, expiresAt)));
    }

    private static UUID actorId(String actor) {
        if (actor == null || !actor.startsWith(ACTOR_PREFIX)) {
            throw new SecurityException("verified Minecraft staff actor required");
        }
        String id = actor.substring(ACTOR_PREFIX.length());
        try {
            UUID parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id)) {
                throw new SecurityException("non-canonical staff actor");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new SecurityException("invalid Minecraft staff actor", exception);
        }
    }

    private byte[] preimage(
            AuthorityBackedCaseActions.Command command, long issuedAt, long expiresAt) {
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
