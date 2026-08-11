package net.enthusia.staff.velocity;

import java.net.URI;

final class DiscordWebhookUriParser {
    private DiscordWebhookUriParser() {
    }

    static URI parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("A required Discord webhook environment variable is missing");
        }
        try {
            return URI.create(raw.trim());
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException("A Discord webhook environment variable is not a valid URI");
        }
    }
}
