package net.enthusia.staff.discordbot;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;

/** Formats public Minecraft chat without trusting player text for Discord mention identity. */
final class DiscordChatMessagePresentation {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern CHAT_TAG = Pattern.compile(
            "(?i)</?(?:reset|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|"
            + "gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|bold|italic|"
            + "underlined|strikethrough|obfuscated|color|gradient|rainbow|font)"
            + "(?:[:=#][^>]{0,120})?>");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)[§&][0-9a-fk-or]");
    private static final Pattern INTERACTIVE_TOKEN = Pattern.compile(
            "(?i)<chat=[a-z0-9-]{15,100}:(?:\\[[a-z]\\]:)?\\s*>");
    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~`>|]");

    private DiscordChatMessagePresentation() {
    }

    static Optional<String> mainDiscordUserId(UUID playerId,
            Optional<MainMinecraftAccount> mainAccount, Set<DiscordUserId> discordIds) {
        if (playerId == null || mainAccount == null || discordIds == null
                || mainAccount.isEmpty() || !mainAccount.orElseThrow().playerId().equals(playerId)
                || discordIds.size() != 1) {
            return Optional.empty();
        }
        String id = discordIds.iterator().next().value();
        return id.matches("[0-9]{15,20}") ? Optional.of(id) : Optional.empty();
    }

    static String minecraftName(Optional<String> canonicalUsername, String decoratedName) {
        if (canonicalUsername != null && canonicalUsername.isPresent()) {
            String username = canonicalUsername.orElseThrow();
            if (USERNAME.matcher(username).matches()) {
                return username;
            }
        }
        String normalized = stripCodes(decoratedName == null ? "" : decoratedName)
                .replace("**", "")
                .replaceAll("\\[[^\\]\\r\\n]{1,40}\\]", " ")
                .trim();
        int separator = normalized.indexOf(':');
        String senderPart = separator < 0 ? normalized : normalized.substring(0, separator);
        Matcher matcher = USERNAME.matcher(senderPart);
        return matcher.find() ? matcher.group() : "Player";
    }

    static String sender(Optional<String> verifiedMainDiscordId, String minecraftName) {
        // Linked Discord identity is not a notification authorization.
        // Even a verified Minecraft main account must not ping on every chat post.
        return escapeMarkdown(minecraftName);
    }

    static String body(String source) {
        String cleaned = stripCodes(source == null ? "" : source);
        // InteractiveChat correlation tokens are not user-facing chat text.
        boolean itemShare = INTERACTIVE_TOKEN.matcher(cleaned).find();
        cleaned = INTERACTIVE_TOKEN.matcher(cleaned).replaceAll("").strip();
        if (cleaned.isEmpty() && itemShare) {
            return "[Item]";
        }
        return escapeMarkdown(cleaned);
    }

    private static String stripCodes(String source) {
        return LEGACY_COLOR.matcher(CHAT_TAG.matcher(source).replaceAll("")).replaceAll("");
    }

    private static String escapeMarkdown(String value) {
        Matcher matcher = MARKDOWN.matcher(value);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement("\\" + matcher.group()));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
