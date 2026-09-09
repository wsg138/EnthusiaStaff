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
                .setTitle("Moderation preview · " + target.displayName())
                .setDescription("Selected target: @" + target.username())
                .setThumbnail(target.avatarUrl())
                .addField("Selected target", target.displayName() + " · Discord " + target.discordId(), false)
                .addField("Investigation channel", target.channelLabel(), true)
                .addField("Panel data", "Real reads for this selected target", true)
                .addField("Actions", "Simulation only — no punishment, DM, restriction, or message deletion is applied.", false)
                .setFooter("STAGING · REAL READS / SIMULATED ACTIONS");
        return new Rendered(embed.build(), List.of(ActionRow.of(button(launchUri))));
    }

    private static Button button(Optional<URI> launchUri) {
        return launchUri
                .map(uri -> Button.link(uri.toString(), "Open Moderation Panel"))
                .orElseGet(() -> Button.secondary("preview-web-unavailable", "Panel deployment required").asDisabled());
    }
}
