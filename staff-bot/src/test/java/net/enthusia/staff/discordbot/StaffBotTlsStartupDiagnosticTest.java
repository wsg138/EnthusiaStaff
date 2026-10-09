package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class StaffBotTlsStartupDiagnosticTest {
    private static final String EXPECTED =
            "63dd196e42bb0aa08221581014166431c712b629a769b4ecdc25e0448c7ad73a";

    @Test
    void embedsOnlyTheExpectedPublicVelocityCertificate() throws Exception {
        X509Certificate certificate = StaffBotTlsStartupDiagnostic.trustedCertificate();
        assertNotNull(certificate);
        certificate.checkValidity();
        assertEquals(EXPECTED, HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
        assertTrue(certificate.getSubjectAlternativeNames().stream().anyMatch(entry ->
                "25319956-7c92-49d1-9afe-ea6e18758016".equals(entry.get(1))));
        try (InputStream stream = getClass().getResourceAsStream("/velocity-channel-trusted.pem")) {
            assertNotNull(stream);
            String publicPem = new String(stream.readAllBytes(), StandardCharsets.US_ASCII);
            assertTrue(publicPem.contains("BEGIN CERTIFICATE"));
            assertFalse(publicPem.contains("PRIVATE KEY"));
        }
    }
}
