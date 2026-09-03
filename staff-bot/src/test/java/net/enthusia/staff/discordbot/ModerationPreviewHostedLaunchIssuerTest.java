package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ModerationPreviewHostedLaunchIssuerTest {
    private static final String TOKEN = "staging-test-discord-token-value";

    @Test
    void hostedTicketIsActorGuildTargetBoundShortLivedAndSignedWithDerivedKey() throws Exception {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_787_000_000L), ZoneOffset.UTC);
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(new byte[] {1, 2, 3, 4});
        ModerationPreviewHostedLaunchIssuer issuer = new ModerationPreviewHostedLaunchIssuer(
                URI.create("https://staff-staging.enthusia.info"),
                ModerationPreviewHostedLaunchIssuer.deriveSigningKey(TOKEN),
                clock,
                random);

        URI uri = issuer.issueLaunchUri(123456789012345678L, 1410303324745371709L);

        assertEquals("https", uri.getScheme());
        assertEquals("staff-staging.enthusia.info", uri.getHost());
        assertEquals("/launch", uri.getPath());
        assertFalse(uri.toString().contains(TOKEN));
        String token = URLDecoder.decode(uri.getRawQuery().substring(2), StandardCharsets.UTF_8);
        String[] pieces = token.split("\\.");
        assertEquals(2, pieces.length);
        String body = new String(Base64.getUrlDecoder().decode(pieces[0]), StandardCharsets.UTF_8);
        String[] fields = body.split("\\|");
        assertEquals("v1", fields[0]);
        assertEquals("staging", fields[1]);
        assertEquals("123456789012345678", fields[3]);
        assertEquals("1410303324745371709", fields[4]);
        assertEquals(ModerationPreviewHostedLaunchIssuer.SAMPLE_TARGET, fields[5]);
        assertEquals(120L, Long.parseLong(fields[7]) - Long.parseLong(fields[6]));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ModerationPreviewHostedLaunchIssuer.deriveSigningKey(TOKEN), "HmacSHA256"));
        assertArrayEquals(
                mac.doFinal(pieces[0].getBytes(StandardCharsets.UTF_8)),
                Base64.getUrlDecoder().decode(pieces[1]));
        assertTrue(fields[2].length() >= 32);
    }
}
