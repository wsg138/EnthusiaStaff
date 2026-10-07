package net.enthusia.staff.discordbot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Separate Discord application identity used only for public Minecraft chat. */
record PublicChatDiscordConfiguration(String token, long applicationId) {
    static final String TOKEN_ENV = "ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_TOKEN";
    static final String APPLICATION_ID_ENV = "ENTHUSIA_STAFF_BOT_PUBLIC_CHAT_APPLICATION_ID";
    private static final long MIN_APPLICATION_ID = 1L;

    PublicChatDiscordConfiguration {
        token = requireToken(token);
        if (applicationId < MIN_APPLICATION_ID) {
            throw new IllegalArgumentException(APPLICATION_ID_ENV + " must be a positive Discord application ID");
        }
    }

    static Optional<PublicChatDiscordConfiguration> fromEnvironment(
            Map<String, String> values,
            boolean chatBridgeEnabled
    ) {
        Objects.requireNonNull(values, "values");
        String token = values.get(TOKEN_ENV);
        String applicationId = values.get(APPLICATION_ID_ENV);
        if (!chatBridgeEnabled) {
            if (blank(token) && blank(applicationId)) {
                return Optional.empty();
            }
            if (blank(token) || blank(applicationId)) {
                throw new IllegalArgumentException(
                        "public chat Discord token and application ID must be configured together");
            }
            new PublicChatDiscordConfiguration(token, parseApplicationId(applicationId));
            return Optional.empty();
        }
        return Optional.of(new PublicChatDiscordConfiguration(
                requireToken(token),
                parseApplicationId(applicationId)
        ));
    }

    @Override
    public String toString() {
        return "PublicChatDiscordConfiguration[applicationId=" + applicationId
                + ", token=<redacted>]";
    }

    private static long parseApplicationId(String value) {
        if (blank(value)) {
            throw new IllegalArgumentException(APPLICATION_ID_ENV + " is required");
        }
        try {
            long parsed = Long.parseUnsignedLong(value.trim());
            if (parsed < MIN_APPLICATION_ID) {
                throw new IllegalArgumentException(APPLICATION_ID_ENV + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    APPLICATION_ID_ENV + " must be an unsigned Discord application ID",
                    exception
            );
        }
    }

    private static String requireToken(String value) {
        if (blank(value)) {
            throw new IllegalArgumentException(TOKEN_ENV + " is required");
        }
        return value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
