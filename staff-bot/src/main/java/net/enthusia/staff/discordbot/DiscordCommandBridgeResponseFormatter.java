package net.enthusia.staff.discordbot;

import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;

/** Converts command bridge results into bounded Discord-safe text without exposing transport internals. */
public final class DiscordCommandBridgeResponseFormatter {
    private static final int MAX_DISCORD_TEXT = 1_900;

    private DiscordCommandBridgeResponseFormatter() {
    }

    public static String format(DiscordCommandBridgeCoordinator.Result result) {
        if (result == null) {
            throw new IllegalArgumentException("command bridge result is required");
        }
        String text = switch (result.status()) {
            case RECEIVED -> formatResponse(result.response().orElseThrow());
            case UNLINKED_ACTOR -> "Your Discord account is not linked to a current Enthusia main Minecraft account.";
            case INVALID_SERVER -> "That Minecraft server is not an allowed console target.";
            case ENDPOINT_UNAVAILABLE -> "The Minecraft command endpoint is unavailable. No command retry was attempted.";
            case AMBIGUOUS_FAILURE -> "Command delivery became ambiguous after dispatch may have started. It was not retried; verify server state before trying again.";
            case INVALID_RESPONSE -> "The Minecraft endpoint returned an invalid authenticated response. No retry was attempted.";
        };
        return bound(safeDiscord(text));
    }

    private static String formatResponse(CommandBridgeResponse response) {
        if (response.outcome() != CommandBridgeOutcome.SUCCESS || response.output().isBlank()) {
            return response.message();
        }
        StringBuilder text = new StringBuilder(response.message()).append("\nOutput:\n").append(response.output());
        if (response.redacted()) {
            text.append("\n[Some output was redacted.]");
        }
        if (response.truncated()) {
            text.append("\n[Output was truncated.]");
        }
        return text.toString();
    }

    private static String safeDiscord(String value) {
        return value.replace("@", "@\u200B").replace('`', '\'');
    }

    private static String bound(String value) {
        if (value.length() <= MAX_DISCORD_TEXT) {
            return value;
        }
        return value.substring(0, MAX_DISCORD_TEXT - 20) + "\n[Response truncated.]";
    }
}
