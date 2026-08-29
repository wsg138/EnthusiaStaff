package net.enthusia.staff.discordbot;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Compact Discord launch surface for the web-first staging moderation console. */
final class ModerationPreviewLauncherPresentation {
    private static final int PANEL_COLOR = 0x313338;

    record Rendered(MessageEmbed embed, List<ActionRow> rows) {
    }

    Rendered render(Optional<URI> launchUri) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(PANEL_COLOR)
                .setTitle("Moderation · RiverAsh")
                .setDescription("@riverash  ·  RiverAshMC")
                .addField("Status", "Discord mute · 1h 18m remaining", true)
                .addField("Linked accounts", "Main + 1 alt", true)
                .addField("Recent history", "2 relevant spam incidents · 4 total records", false)
                .setFooter("STAGING PREVIEW");
        return new Rendered(embed.build(), List.of(ActionRow.of(button(launchUri))));
    }

    private static Button button(Optional<URI> launchUri) {
        return launchUri
                .map(uri -> Button.link(uri.toString(), "Open Moderation Panel"))
                .orElseGet(() -> Button.secondary("preview-web-unavailable", "Panel deployment required").asDisabled());
    }
}
