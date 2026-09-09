package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class ModerationPreviewLauncherPresentationTest {
    private final ModerationPreviewLauncherPresentation presentation = new ModerationPreviewLauncherPresentation();

    @Test
    void launcherUsesSelectedTargetAndMakesSimulationBoundaryExplicit() {
        URI launch = URI.create("https://staff-staging.enthusia.info/launch?t=signed");
        var target = new ModerationPreviewLauncherPresentation.TargetSummary(
                "P2wn", "p2wn", "846729778400460871", "#staff-chat",
                "https://cdn.discordapp.com/avatars/846729778400460871/avatar.png");

        ModerationPreviewLauncherPresentation.Rendered rendered = presentation.render(Optional.of(launch), target);
        MessageEmbed embed = rendered.embed();

        assertEquals("Moderation preview · P2wn", embed.getTitle());
        assertEquals("Selected target: @p2wn", embed.getDescription());
        assertEquals(target.avatarUrl(), embed.getThumbnail().getUrl());
        assertTrue(fieldValue(embed, "Selected target").contains(target.discordId()));
        assertEquals("#staff-chat", fieldValue(embed, "Investigation channel"));
        assertEquals("Real reads for this selected target", fieldValue(embed, "Panel data"));
        assertTrue(fieldValue(embed, "Actions").contains("Simulation only"));
        assertEquals("STAGING · REAL READS / SIMULATED ACTIONS", embed.getFooter().getText());
        assertFalse(embed.toData().toString().contains("RiverAsh"));
        assertFalse(embed.toData().toString().contains("mute"));
        assertFalse(embed.toData().toString().contains("prior incidents"));
        Button button = (Button) rendered.rows().getFirst().getComponents().getFirst();
        assertEquals(launch.toString(), button.getUrl());
    }

    private static String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .map(MessageEmbed.Field::getValue)
                .findFirst()
                .orElseThrow();
    }
}
