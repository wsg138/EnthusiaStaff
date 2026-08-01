package net.enthusia.staff.velocity;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class WebsiteApiTestSignatures {
    static final String BEARER = Character.toString('b').repeat(48);
    static final String HMAC = Character.toString('h').repeat(48);

    private WebsiteApiTestSignatures() {
    }

    static Headers signedHeaders(
            String method,
            String target,
            byte[] body,
            Instant timestamp,
            String nonce
    ) {
        String bodyHash = base64Url(sha256(body));
        String time = Long.toString(timestamp.toEpochMilli());
        String canonical = method + '\n' + target + '\n' + time + '\n' + nonce + '\n' + bodyHash;
        Headers headers = new Headers();
        headers.set("authorization", "Bearer " + BEARER);
        headers.set("x-enthusia-timestamp", time);
        headers.set("x-enthusia-nonce", nonce);
        headers.set("x-enthusia-content-sha256", bodyHash);
        headers.set("x-enthusia-signature", base64Url(hmac(canonical)));
        return headers;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
