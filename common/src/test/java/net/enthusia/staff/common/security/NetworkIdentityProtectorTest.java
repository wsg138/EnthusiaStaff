package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class NetworkIdentityProtectorTest {
    @Test
    void createsStableEqualityTokensAndRandomizedRecoverableCiphertext() {
        byte[] keyBytes = new byte[32];
        java.util.Arrays.fill(keyBytes, (byte) 7);
        String encoded = Base64.getEncoder().encodeToString(keyBytes);
        SecretKey hmac = SecretKeyMaterial.hmacSha256FromBase64(encoded);
        SecretKey aes = SecretKeyMaterial.aesFromBase64(encoded);
        NetworkIdentityProtector protector = new NetworkIdentityProtector(
                new HmacTokenService(2, hmac), 4, aes, new SecureRandom()
        );
        byte[] address = new byte[]{10, 20, 30, 40};

        ProtectedNetworkIdentity first = protector.protect(address);
        ProtectedNetworkIdentity second = protector.protect(address);

        assertArrayEquals(first.equalityToken(), second.equalityToken());
        assertFalse(java.util.Arrays.equals(first.encryptedValue(), second.encryptedValue()));
        assertArrayEquals(address, protector.recover(first));
    }
}
