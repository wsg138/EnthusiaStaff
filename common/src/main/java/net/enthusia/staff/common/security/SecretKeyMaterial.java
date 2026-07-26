package net.enthusia.staff.common.security;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class SecretKeyMaterial {
    private static final int MINIMUM_KEY_BYTES = 32;

    private SecretKeyMaterial() {
    }

    public static SecretKey hmacSha256FromBase64(String encoded) {
        return fromBase64(encoded, "HmacSHA256");
    }

    public static SecretKey aesFromBase64(String encoded) {
        return fromBase64(encoded, "AES");
    }

    private static SecretKey fromBase64(String encoded, String algorithm) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Secret key material is missing");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Secret key material is not valid base64", exception);
        }
        if (bytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Secret key material must contain at least 32 random bytes");
        }
        if (algorithm.equals("AES") && bytes.length != 32) {
            throw new IllegalArgumentException("AES-256 key material must contain exactly 32 random bytes");
        }
        return new SecretKeySpec(bytes, algorithm);
    }
}
