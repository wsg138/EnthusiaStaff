package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretKeyMaterialTest {
    @Test
    void requiresAtLeast256BitsOfDecodedKeyMaterial() {
        String valid = Base64.getEncoder().encodeToString(new byte[32]);
        assertEquals("HmacSHA256", SecretKeyMaterial.hmacSha256FromBase64(valid).getAlgorithm());
        assertThrows(IllegalArgumentException.class, () ->
                SecretKeyMaterial.hmacSha256FromBase64(Base64.getEncoder().encodeToString(new byte[31])));
    }
}
