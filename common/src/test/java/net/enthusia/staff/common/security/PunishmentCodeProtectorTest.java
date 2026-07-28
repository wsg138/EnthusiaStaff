package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class PunishmentCodeProtectorTest {
    private static final SecretKey KEY = SecretKeyMaterial.hmacSha256FromBase64(
            Base64.getEncoder().encodeToString(new byte[32])
    );

    @Test
    void derivesStableHighEntropyCodesWithoutStoringPlaintext() {
        PunishmentCodeProtector protector = new PunishmentCodeProtector(3, KEY);
        UUID sanction = UUID.fromString("070d835d-154f-47fc-94bd-514766f21d6d");

        String first = protector.code(sanction, 1);
        assertEquals(29, first.length());
        assertEquals(24, protector.normalize(first).length());
        assertEquals(first, protector.code(sanction, 1));
        assertNotEquals(first, protector.code(sanction, 2));
        assertArrayEquals(protector.verificationHash(first), protector.verificationHash(first.replace("-", " ")));
    }

    @Test
    void domainSeparatesAccountAndCodeTokens() {
        PunishmentCodeProtector protector = new PunishmentCodeProtector(1, KEY);
        String value = "070d835d-154f-47fc-94bd-514766f21d6d";
        assertNotEquals(
                java.util.HexFormat.of().formatHex(protector.accountToken(value)),
                java.util.HexFormat.of().formatHex(protector.verificationHash(
                        protector.code(UUID.fromString(value), 1)
                ))
        );
    }

    @Test
    void rejectsAmbiguousOrMalformedCodes() {
        PunishmentCodeProtector protector = new PunishmentCodeProtector(1, KEY);
        assertThrows(IllegalArgumentException.class, () -> protector.normalize("contains-0-or-1"));
        assertThrows(IllegalArgumentException.class, () -> protector.accountToken("not-a-uuid"));
    }
}
