package net.enthusia.staff.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public final class PunishmentCodeProtector {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_BYTES = 15;
    private static final int CODE_CHARACTERS = 24;

    private final int keyVersion;
    private final SecretKey key;

    public PunishmentCodeProtector(int keyVersion, SecretKey key) {
        if (keyVersion < 1 || key == null) {
            throw new IllegalArgumentException("A positive key version and secret key are required");
        }
        this.keyVersion = keyVersion;
        this.key = key;
    }

    public int keyVersion() {
        return keyVersion;
    }

    public String code(UUID sanctionId, int generation) {
        if (sanctionId == null || generation < 1) {
            throw new IllegalArgumentException("Sanction ID and positive generation are required");
        }
        ByteBuffer identifier = ByteBuffer.allocate(20)
                .putLong(sanctionId.getMostSignificantBits())
                .putLong(sanctionId.getLeastSignificantBits())
                .putInt(generation);
        byte[] digest = hmac("derive", identifier.array());
        String normalized = base32(digest, CODE_BYTES);
        return normalized.substring(0, 4) + '-' + normalized.substring(4, 8) + '-'
                + normalized.substring(8, 12) + '-' + normalized.substring(12, 16) + '-'
                + normalized.substring(16, 20) + '-' + normalized.substring(20);
    }

    public String normalize(String supplied) {
        if (supplied == null) {
            throw new IllegalArgumentException("Punishment code is required");
        }
        String normalized = supplied.replace("-", "")
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.length() != CODE_CHARACTERS
                || normalized.chars().anyMatch(character -> ALPHABET.indexOf(character) < 0)) {
            throw new IllegalArgumentException("Punishment code format is invalid");
        }
        return normalized;
    }

    public byte[] verificationHash(String supplied) {
        return hmac("verify", normalize(supplied).getBytes(StandardCharsets.US_ASCII));
    }

    public byte[] accountToken(String opaqueAccountId) {
        if (opaqueAccountId == null || !opaqueAccountId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Opaque account ID must be a UUID");
        }
        return hmac("account", opaqueAccountId.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] hmac(String domain, byte[] value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(("enthusia-punishment-code:" + domain + '\0').getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC provider is unavailable", exception);
        }
    }

    private static String base32(byte[] bytes, int byteCount) {
        StringBuilder encoded = new StringBuilder(CODE_CHARACTERS);
        int buffer = 0;
        int bits = 0;
        for (int index = 0; index < byteCount; index++) {
            buffer = (buffer << 8) | Byte.toUnsignedInt(bytes[index]);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                encoded.append(ALPHABET.charAt((buffer >>> bits) & 31));
            }
        }
        if (bits > 0) {
            encoded.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        if (encoded.length() != CODE_CHARACTERS) {
            throw new IllegalStateException("Punishment code encoding produced an unexpected length");
        }
        return encoded.toString();
    }
}
