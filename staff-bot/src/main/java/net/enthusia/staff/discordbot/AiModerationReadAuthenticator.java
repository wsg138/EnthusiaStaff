package net.enthusia.staff.discordbot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time bearer authentication for the read-only Enthusia AI bridge. */
final class AiModerationReadAuthenticator {
    private static final String PREFIX = "Bearer ";
    private final byte[] expected;

    AiModerationReadAuthenticator(String token) {
        if (token == null || token.isBlank() || token.length() < 32 || token.length() > 512) {
            throw new IllegalArgumentException("AI moderation read token must be 32-512 characters");
        }
        expected = token.getBytes(StandardCharsets.UTF_8);
    }

    boolean accepts(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            return false;
        }
        byte[] provided = authorization.substring(PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
