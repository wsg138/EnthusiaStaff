package net.enthusia.staff.discordbot;

import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/** Discord-only presentation for the read-only moderation surface. */
final class StaffModerationDiscordPresentation {
    private static final int EMBED_COLOR = 0x5865F2;
    private static final String FALLBACK_TITLE = "Moderation";
    private static final String FOOTER = "Private staff view • Read only";
    private static final Map<String, String> DISPLAY_LABELS = Map.of(
            "Refresh", "Overview",
            "Linked", "Accounts"
    );
    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("Refresh", "🏠"),
            Map.entry("History", "🕘"),
            Map.entry("Linked", "🔗"),
            Map.entry("Notes", "📝"),
            Map.entry("Cases", "📁"),
            Map.entry("All", "📋"),
            Map.entry("Discord", "💬"),
            Map.entry("Minecraft", "⛏️")
    );

    private StaffModerationDiscordPresentation() {
    }

    static MessageEmbed embed(String content) {
        Heading heading = splitHeading(content);
        EmbedBuilder builder = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle("🛡️ " + friendlyTitle(heading.title()))
                .setDescription(heading.body())
                .setFooter(FOOTER);
        return builder.build();
    }

    static Button button(StaffModerationController.Button source, String content) {
        String rawLabel = source.label();
        String displayLabel = DISPLAY_LABELS.getOrDefault(rawLabel, rawLabel);
        boolean selected = rawLabel.equals(selectedLabel(content));
        Button button = selected
                ? Button.primary(source.customId(), displayLabel)
                : Button.secondary(source.customId(), displayLabel);
        String icon = ICONS.get(rawLabel);
        return icon == null ? button : button.withEmoji(Emoji.fromUnicode(icon));
    }

    private static String selectedLabel(String content) {
        String title = splitHeading(content).title();
        if (title.equals("Moderation profile")) {
            return "Refresh";
        }
        if (title.equals("Linked accounts")) {
            return "Linked";
        }
        if (title.equals("Recent staff notes")) {
            return "Notes";
        }
        if (title.equals("Recent cases")) {
            return "Cases";
        }
        if (title.equals("History — Discord")) {
            return "Discord";
        }
        if (title.equals("History — Minecraft")) {
            return "Minecraft";
        }
        if (title.startsWith("History —")) {
            return "All";
        }
        return "";
    }

    private static String friendlyTitle(String title) {
        return switch (title) {
            case "Moderation profile" -> "Moderation • Overview";
            case "Linked accounts" -> "Moderation • Accounts";
            case "History — All" -> "Moderation • History";
            case "History — Discord" -> "Moderation • Discord History";
            case "History — Minecraft" -> "Moderation • Minecraft History";
            case "Recent staff notes" -> "Moderation • Notes";
            case "Recent cases" -> "Moderation • Cases";
            default -> title;
        };
    }

    private static Heading splitHeading(String content) {
        int newline = content.indexOf('\n');
        if (newline <= 4 || !content.startsWith("**")) {
            return new Heading(FALLBACK_TITLE, content);
        }
        String firstLine = content.substring(0, newline);
        if (!firstLine.endsWith("**")) {
            return new Heading(FALLBACK_TITLE, content);
        }
        String title = firstLine.substring(2, firstLine.length() - 2);
        String body = content.substring(newline + 1);
        return new Heading(title, body.isBlank() ? "No details are available." : body);
    }

    private record Heading(String title, String body) {
    }
}
