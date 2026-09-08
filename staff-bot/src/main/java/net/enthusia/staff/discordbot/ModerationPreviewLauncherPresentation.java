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

    record TargetSummary(
            String displayName,
            String username,
            String discordId,
            String channelLabel,
            String avatarUrl
    ) {
        TargetSummary {
            if (blank(displayName) || blank(username) || blank(discordId) || blank(channelLabel) || blank(avatarUrl)) {
                throw new IllegalArgumentException("moderation preview target summary is incomplete");
            }
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    record Rendered(MessageEmbed embed, List<ActionRow> rows) {
    }

    Rendered render(Optional<URI> launchUri, TargetSummary target) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(PANEL_COLOR)
                .setTitle("Moderation · " + target.displayName())
                .setDescription("@" + target.username())
                .setThumbnail(target.avatarUrl())
                .addField("Target", target.displayName() + " · " + target.discordId(), false)
                .addField("Channel", target.channelLabel(), true)
                .addField("Workspace", "Real read-only context · staging moderation simulation", true)
                .setFooter("STAGING PREVIEW");
        return new Rendered(embed.build(), List.of(ActionRow.of(button(launchUri))));
    }

    private static Button button(Optional<URI> launchUri) {
        return launchUri
                .map(uri -> Button.link(uri.toString(), "Open Moderation Panel"))
                .orElseGet(() -> Button.secondary("preview-web-unavailable", "Panel deployment required").asDisabled());
    }
}
