package net.enthusia.staff.discordbot;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.enthusia.staff.common.CaseId;

/** Compact HMAC-signed component IDs bound to one staff invoker, target and short expiry. */
final class SignedComponentCodec {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "d6";
    private static final int SIGNATURE_BYTES = 12;
    private static final int MAX_CUSTOM_ID_LENGTH = 100;
    private static final int COMPONENT_PART_COUNT = 8;
    private static final int SIGNATURE_PART = COMPONENT_PART_COUNT - 1;
    private static final int UUID_HEX_LENGTH = 32;
    private static final int UUID_TEXT_LENGTH = 36;

    enum Action {
        PROFILE("p"),
        HISTORY("h"),
        HISTORY_DISCORD("i"),
        HISTORY_MINECRAFT("j"),
        LINKED("l"),
        NOTES("n"),
        CASES("c"),
        SELECT_MINECRAFT("s"),
        CASE("k");

        private final String wireCode;

        Action(String code) {
            this.wireCode = code;
        }

        String code() {
            return wireCode;
        }

        static Action parse(String value) {
            for (Action action : values()) {
                if (action.wireCode.equals(value)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("unknown component action");
        }
    }

    enum TargetType {
        DISCORD("d"),
        MINECRAFT("m"),
        CASE("c"),
        NONE("x");

        private final String wireCode;

        TargetType(String code) {
            this.wireCode = code;
        }

        static TargetType parse(String value) {
            for (TargetType type : values()) {
                if (type.wireCode.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("unknown target type");
        }
    }

    record TargetRef(TargetType type, String value) {
        TargetRef {
            if (type == null || value == null || value.isBlank()) {
                throw new IllegalArgumentException("component target fields must be present");
            }
        }

        static TargetRef discord(long userId) {
            if (userId <= 0) {
                throw new IllegalArgumentException("Discord user id must be positive");
            }
            return new TargetRef(TargetType.DISCORD, Long.toString(userId, 36));
        }

        static TargetRef minecraft(UUID playerId) {
            if (playerId == null) {
                throw new IllegalArgumentException("playerId must be present");
            }
            return new TargetRef(TargetType.MINECRAFT, playerId.toString().replace("-", ""));
        }

        static TargetRef caseId(CaseId caseId) {
            if (caseId == null) {
                throw new IllegalArgumentException("caseId must be present");
            }
            return new TargetRef(TargetType.CASE, caseId.value().toLowerCase(Locale.ROOT));
        }

        static TargetRef none() {
            return new TargetRef(TargetType.NONE, "0");
        }

        long discordId() {
            if (type != TargetType.DISCORD) {
                throw new IllegalStateException("target is not Discord");
            }
            return Long.parseLong(value, 36);
        }

        UUID minecraftId() {
            if (type != TargetType.MINECRAFT || value.length() != UUID_HEX_LENGTH) {
                throw new IllegalStateException("target is not Minecraft");
            }
            return UUID.fromString(uuidText(value));
        }

        CaseId caseId() {
            if (type != TargetType.CASE) {
                throw new IllegalStateException("target is not a case");
            }
            return new CaseId(value);
        }

        private static String uuidText(String compact) {
            return new StringBuilder(UUID_TEXT_LENGTH)
                    .append(compact, 0, 8)
                    .append('-')
                    .append(compact, 8, 12)
                    .append('-')
                    .append(compact, 12, 16)
                    .append('-')
                    .append(compact, 16, 20)
                    .append('-')
                    .append(compact, 20, compact.length())
                    .toString();
        }
    }

    enum Denial {
        INVALID,
        STALE,
        WRONG_ACTOR,
        REPLAYED,
        SATURATED
    }

    static final class InvalidComponentException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Denial reason;

        InvalidComponentException(Denial denial) {
            super("component denied: " + denial);
            this.reason = denial;
        }

        Denial denial() {
            return reason;
        }
    }

    record Decoded(Action action, TargetRef target) {
    }

    private final Clock clock;
    private final Duration ttl;
    private final SecretKeySpec key;
    private final SecureRandom random;
    private final InteractionReplayGuard replay;

    SignedComponentCodec(
            Clock clock,
            Duration ttl,
            String secret,
            SecureRandom random,
            InteractionReplayGuard replay
    ) {
        if (clock == null || ttl == null || ttl.isZero() || ttl.isNegative()
                || secret == null || secret.isBlank() || random == null || replay == null) {
            throw new IllegalArgumentException("component codec configuration is invalid");
        }
        this.clock = clock;
        this.ttl = ttl;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.random = random;
        this.replay = replay;
    }

    String encode(Action action, TargetRef target, long actorDiscordId) {
        if (action == null || target == null || actorDiscordId <= 0) {
            throw new IllegalArgumentException("component fields are invalid");
        }
        long expires = Math.addExact(clock.instant().getEpochSecond(), ttl.toSeconds());
        long nonce = random.nextLong() & Long.MAX_VALUE;
        if (nonce == 0) {
            nonce = 1;
        }
        String payload = String.join(":",
                PREFIX,
                action.code(),
                target.type().wireCode,
                target.value(),
                Long.toString(actorDiscordId, 36),
                Long.toString(expires, 36),
                Long.toString(nonce, 36));
        String encoded = payload + ":" + signature(payload);
        if (encoded.length() > MAX_CUSTOM_ID_LENGTH) {
            throw new IllegalStateException("signed component ID exceeds Discord limit");
        }
        return encoded;
    }

    Decoded decodeAndClaim(String customId, long actorDiscordId) {
        try {
            String[] parts = componentParts(customId);
            verifySignature(parts);
            verifyActor(parts, actorDiscordId);
            verifyExpiry(parts);
            claimNonce(parts);
            return new Decoded(Action.parse(parts[1]), decodeTarget(TargetType.parse(parts[2]), parts[3]));
        } catch (InvalidComponentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw denial(Denial.INVALID);
        }
    }

    private static String[] componentParts(String customId) {
        String[] parts = customId == null ? new String[0] : customId.split(":", -1);
        if (parts.length != COMPONENT_PART_COUNT || !PREFIX.equals(parts[0])) {
            throw denial(Denial.INVALID);
        }
        return parts;
    }

    private void verifySignature(String[] parts) {
        String payload = String.join(":", parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6]);
        if (!constantTimeEquals(signature(payload), parts[SIGNATURE_PART])) {
            throw denial(Denial.INVALID);
        }
    }

    private static void verifyActor(String[] parts, long actorDiscordId) {
        long encodedActor = Long.parseLong(parts[4], 36);
        if (encodedActor != actorDiscordId) {
            throw denial(Denial.WRONG_ACTOR);
        }
    }

    private void verifyExpiry(String[] parts) {
        long expires = Long.parseLong(parts[5], 36);
        if (clock.instant().getEpochSecond() >= expires) {
            throw denial(Denial.STALE);
        }
    }

    private void claimNonce(String[] parts) {
        long nonce = Long.parseLong(parts[6], 36);
        InteractionReplayGuard.ClaimResult claim = replay.claim(nonce);
        if (claim == InteractionReplayGuard.ClaimResult.DUPLICATE) {
            throw denial(Denial.REPLAYED);
        }
        if (claim == InteractionReplayGuard.ClaimResult.SATURATED) {
            throw denial(Denial.SATURATED);
        }
    }

    private TargetRef decodeTarget(TargetType type, String encoded) {
        return switch (type) {
            case DISCORD -> TargetRef.discord(Long.parseLong(encoded, 36));
            case MINECRAFT -> TargetRef.minecraft(new TargetRef(type, encoded).minecraftId());
            case CASE -> TargetRef.caseId(new CaseId(encoded));
            case NONE -> TargetRef.none();
        };
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = java.util.Arrays.copyOf(full, SIGNATURE_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC provider is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private static InvalidComponentException denial(Denial denial) {
        return new InvalidComponentException(denial);
    }
}
