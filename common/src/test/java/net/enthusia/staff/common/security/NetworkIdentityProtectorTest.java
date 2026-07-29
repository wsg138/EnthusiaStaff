package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class NetworkIdentityProtectorTest {
    @Test
    void createsStableEqualityTokensAndRandomizedRecoverableCiphertext() {
        NetworkIdentityProtector protector = protector(new SecureRandom());
        byte[] address = new byte[]{10, 20, 30, 40};

        ProtectedNetworkIdentity first = protector.protect(address);
        ProtectedNetworkIdentity second = protector.protect(address);

        assertArrayEquals(first.equalityToken(), second.equalityToken());
        assertFalse(java.util.Arrays.equals(first.encryptedValue(), second.encryptedValue()));
        assertArrayEquals(address, protector.recover(first));
    }

    @Test
    void requestsAFreshNonceForEveryEncryption() {
        NetworkIdentityProtector protector = protector(new SequencedSecureRandom());

        ProtectedNetworkIdentity first = protector.protect(new byte[]{10, 20, 30, 40});
        ProtectedNetworkIdentity second = protector.protect(new byte[]{10, 20, 30, 40});

        assertArrayEquals(filledNonce((byte) 1), nonce(first));
        assertArrayEquals(filledNonce((byte) 2), nonce(second));
    }

    @Test
    void rejectsRecoveryThroughAnotherEncryptionKeyVersion() {
        NetworkIdentityProtector protector = protector(new SequencedSecureRandom());
        ProtectedNetworkIdentity identity = protector.protect(new byte[]{10, 20, 30, 40});
        ProtectedNetworkIdentity wrongVersion = new ProtectedNetworkIdentity(
                identity.equalityKeyVersion(),
                identity.equalityToken(),
                identity.encryptionKeyVersion() + 1,
                identity.encryptedValue()
        );

        assertThrows(IllegalArgumentException.class, () -> protector.recover(wrongVersion));
    }

    @Test
    void rejectsCiphertextThatDoesNotMatchItsEqualityToken() {
        NetworkIdentityProtector protector = protector(new SequencedSecureRandom());
        ProtectedNetworkIdentity identity = protector.protect(new byte[]{10, 20, 30, 40});
        byte[] mismatchedToken = identity.equalityToken();
        mismatchedToken[0] ^= 1;
        ProtectedNetworkIdentity mismatched = new ProtectedNetworkIdentity(
                identity.equalityKeyVersion(),
                mismatchedToken,
                identity.encryptionKeyVersion(),
                identity.encryptedValue()
        );

        assertThrows(IllegalArgumentException.class, () -> protector.recover(mismatched));
    }

    @Test
    void rejectsTamperedAuthenticatedCiphertext() {
        NetworkIdentityProtector protector = protector(new SequencedSecureRandom());
        ProtectedNetworkIdentity identity = protector.protect(new byte[]{10, 20, 30, 40});
        byte[] tamperedValue = identity.encryptedValue();
        tamperedValue[tamperedValue.length - 1] ^= 1;
        ProtectedNetworkIdentity tampered = new ProtectedNetworkIdentity(
                identity.equalityKeyVersion(),
                identity.equalityToken(),
                identity.encryptionKeyVersion(),
                tamperedValue
        );

        assertThrows(IllegalArgumentException.class, () -> protector.recover(tampered));
    }

    @Test
    void recoversIpv6Identity() {
        NetworkIdentityProtector protector = protector(new SequencedSecureRandom());
        byte[] address = new byte[]{
                0x20, 0x01, 0x0d, (byte) 0xb8,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 1
        };

        assertArrayEquals(address, protector.recover(protector.protect(address)));
    }

    @Test
    void rejectsMalformedEnvelopeLengthsBeforeDecryption() {
        NetworkIdentityProtector protector = protector(new SequencedSecureRandom());
        ProtectedNetworkIdentity identity = protector.protect(new byte[]{10, 20, 30, 40});
        byte[] encryptedValue = identity.encryptedValue();

        ProtectedNetworkIdentity truncated =
                copyWithEncryptedValue(identity, Arrays.copyOf(encryptedValue, encryptedValue.length - 1));
        ProtectedNetworkIdentity oversized =
                copyWithEncryptedValue(identity, Arrays.copyOf(encryptedValue, encryptedValue.length + 1));

        assertThrows(IllegalArgumentException.class, () -> protector.recover(truncated));
        assertThrows(IllegalArgumentException.class, () -> protector.recover(oversized));
    }

    @Test
    void rejectsNonAesEncryptionKeysDuringConfiguration() {
        byte[] keyBytes = keyBytes();
        SecretKey hmac = SecretKeyMaterial.hmacSha256FromBase64(
                Base64.getEncoder().encodeToString(keyBytes)
        );
        SecretKey wrongAlgorithm = new SecretKeySpec(keyBytes, "HmacSHA256");

        assertThrows(
                IllegalArgumentException.class,
                () -> new NetworkIdentityProtector(
                        new HmacTokenService(2, hmac),
                        4,
                        wrongAlgorithm,
                        new SecureRandom()
                )
        );
    }

    private static ProtectedNetworkIdentity copyWithEncryptedValue(
            ProtectedNetworkIdentity identity,
            byte[] encryptedValue
    ) {
        return new ProtectedNetworkIdentity(
                identity.equalityKeyVersion(),
                identity.equalityToken(),
                identity.encryptionKeyVersion(),
                encryptedValue
        );
    }

    private static NetworkIdentityProtector protector(SecureRandom random) {
        byte[] keyBytes = keyBytes();
        String encoded = Base64.getEncoder().encodeToString(keyBytes);
        return new NetworkIdentityProtector(
                new HmacTokenService(2, SecretKeyMaterial.hmacSha256FromBase64(encoded)),
                4,
                SecretKeyMaterial.aesFromBase64(encoded),
                random
        );
    }

    private static byte[] keyBytes() {
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 7);
        return keyBytes;
    }

    private static byte[] nonce(ProtectedNetworkIdentity identity) {
        return Arrays.copyOf(identity.encryptedValue(), 12);
    }

    private static byte[] filledNonce(byte value) {
        byte[] nonce = new byte[12];
        Arrays.fill(nonce, value);
        return nonce;
    }

    private static final class SequencedSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        private int invocation;

        @Override
        public void nextBytes(byte[] bytes) {
            invocation++;
            Arrays.fill(bytes, (byte) invocation);
        }
    }
}
