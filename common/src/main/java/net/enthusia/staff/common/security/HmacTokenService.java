package net.enthusia.staff.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public final class HmacTokenService {
    private static final String ALGORITHM = "HmacSHA256";

    private final int keyVersion;
    private final SecretKey key;

    public HmacTokenService(int keyVersion, SecretKey key) {
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        this.keyVersion = keyVersion;
        this.key = key;
    }

    public EqualityToken token(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            throw new IllegalArgumentException("normalizedValue must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
            return new EqualityToken(keyVersion, HexFormat.of().formatHex(digest));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC provider is unavailable", exception);
        }
    }

    public record EqualityToken(int keyVersion, String value) {
        public EqualityToken {
            if (keyVersion < 1 || value == null || value.length() != 64) {
                throw new IllegalArgumentException("invalid equality token");
            }
        }
    }
}
