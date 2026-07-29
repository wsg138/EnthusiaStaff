package net.enthusia.staff.common.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class NetworkIdentityProtector {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;
    private static final int IPV4_BYTES = 4;
    private static final int IPV6_BYTES = 16;
    private static final int IPV4_ENVELOPE_BYTES = NONCE_BYTES + TAG_BYTES + IPV4_BYTES;
    private static final int IPV6_ENVELOPE_BYTES = NONCE_BYTES + TAG_BYTES + IPV6_BYTES;

    private final HmacTokenService equalityTokens;
    private final int encryptionKeyVersion;
    private final SecretKey encryptionKey;
    private final SecureRandom random;

    public NetworkIdentityProtector(
            HmacTokenService equalityTokens,
            int encryptionKeyVersion,
            SecretKey encryptionKey,
            SecureRandom random
    ) {
        if (equalityTokens == null || encryptionKeyVersion < 1 || encryptionKey == null || random == null) {
            throw new IllegalArgumentException("network identity protector configuration is invalid");
        }
        if (!"AES".equalsIgnoreCase(encryptionKey.getAlgorithm())) {
            throw new IllegalArgumentException("network identity encryption key must use AES");
        }
        this.equalityTokens = equalityTokens;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.encryptionKey = encryptionKey;
        this.random = random;
    }

    public ProtectedNetworkIdentity protect(byte[] addressBytes) {
        if (addressBytes == null || (addressBytes.length != 4 && addressBytes.length != 16)) {
            throw new IllegalArgumentException("network address must be an IPv4 or IPv6 byte sequence");
        }
        String normalized = HexFormat.of().formatHex(addressBytes);
        HmacTokenService.EqualityToken equality = equalityTokens.token(normalized);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(ByteBuffer.allocate(Integer.BYTES).putInt(encryptionKeyVersion).array());
            byte[] encrypted = cipher.doFinal(addressBytes);
            byte[] stored = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return new ProtectedNetworkIdentity(
                    equality.keyVersion(),
                    HexFormat.of().parseHex(equality.value()),
                    encryptionKeyVersion,
                    stored
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Network identity encryption provider is unavailable", exception);
        }
    }

    public byte[] recover(ProtectedNetworkIdentity protectedIdentity) {
        if (protectedIdentity == null
                || protectedIdentity.encryptionKeyVersion() != encryptionKeyVersion) {
            throw new IllegalArgumentException("Protected network identity key version is unavailable");
        }
        byte[] stored = protectedIdentity.encryptedValue();
        if (stored.length != IPV4_ENVELOPE_BYTES && stored.length != IPV6_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Protected network identity envelope length is invalid");
        }
        byte[] nonce = Arrays.copyOfRange(stored, 0, NONCE_BYTES);
        byte[] encrypted = Arrays.copyOfRange(stored, NONCE_BYTES, stored.length);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(ByteBuffer.allocate(Integer.BYTES).putInt(
                    protectedIdentity.encryptionKeyVersion()).array());
            byte[] recovered = cipher.doFinal(encrypted);
            requireMatchingEqualityToken(protectedIdentity, recovered);
            return recovered;
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Protected network identity cannot be recovered with this key", exception);
        }
    }

    private void requireMatchingEqualityToken(
            ProtectedNetworkIdentity protectedIdentity,
            byte[] recovered
    ) {
        HmacTokenService.EqualityToken expected =
                equalityTokens.token(HexFormat.of().formatHex(recovered));
        byte[] expectedValue = HexFormat.of().parseHex(expected.value());
        boolean matches = expected.keyVersion() == protectedIdentity.equalityKeyVersion()
                && MessageDigest.isEqual(expectedValue, protectedIdentity.equalityToken());
        Arrays.fill(expectedValue, (byte) 0);
        if (!matches) {
            Arrays.fill(recovered, (byte) 0);
            throw new IllegalArgumentException(
                    "Protected network identity does not match its equality token"
            );
        }
    }
}
