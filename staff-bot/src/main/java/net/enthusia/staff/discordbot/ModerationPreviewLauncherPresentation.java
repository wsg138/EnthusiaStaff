package net.enthusia.staff.discordbot;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Compact Discord launch surface for the web-first moderation workspace. */
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
        MessageEmbed embed = new EmbedBuilder()
                .setColor(PANEL_COLOR)
                .setTitle("Moderation · " + target.displayName())
                .setDescription("Review @" + target.username() + " in " + target.channelLabel())
                .setThumbnail(target.avatarUrl())
                .addField("Channel", target.channelLabel(), false)
                .setFooter("Enthusia Staff · Moderation Workspace")
                .build();
        return new Rendered(embed, List.of(ActionRow.of(button(launchUri))));
    }

    private static Button button(Optional<URI> launchUri) {
        return launchUri
                .map(uri -> Button.link(uri.toString(), "Open Moderation Workspace"))
                .orElseGet(() -> Button.secondary("preview-web-unavailable", "Workspace unavailable").asDisabled());
    }
}
