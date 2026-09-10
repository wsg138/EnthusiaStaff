package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class ModerationPreviewLauncherPresentationTest {
    private static final String STAFF_CHAT = "#staff-chat";
    private final ModerationPreviewLauncherPresentation presentation = new ModerationPreviewLauncherPresentation();

    @Test
    void launcherUsesSelectedTargetWithoutDiagnosticOrTechnicalClutter() {
        URI launch = URI.create("https://staff-staging.enthusia.info/launch?t=signed");
        var target = new ModerationPreviewLauncherPresentation.TargetSummary(
                "P2wn", "p2wn", "846729778400460871", STAFF_CHAT,
                "https://cdn.discordapp.com/avatars/846729778400460871/avatar.png");

        ModerationPreviewLauncherPresentation.Rendered rendered = presentation.render(Optional.of(launch), target);
        MessageEmbed embed = rendered.embed();
        String serialized = embed.toData().toString();
        String normalized = serialized.toLowerCase(Locale.ROOT);

        assertEquals("@p2wn", embed.getTitle());
        assertNull(embed.getDescription());
        assertEquals(target.avatarUrl(), embed.getThumbnail().getUrl());
        assertEquals(STAFF_CHAT, fieldValue(embed, "Channel"));
        assertEquals("Enthusia Staff · Moderation Workspace", embed.getFooter().getText());
        assertFalse(embed.getTitle().contains(target.discordId()));
        assertFalse(fieldValue(embed, "Channel").contains(target.discordId()));
        assertFalse(embed.getFooter().getText().contains(target.discordId()));
        assertFalse(serialized.contains("RiverAsh"));
        assertFalse(normalized.contains("staging"));
        assertFalse(normalized.contains("simulation"));
        assertFalse(normalized.contains("preview"));
        Button button = (Button) rendered.rows().getFirst().getComponents().getFirst();
        assertEquals("Open Moderation Workspace", button.getLabel());
        assertEquals(launch.toString(), button.getUrl());
    }

    @Test
    void channelLauncherHasNoSyntheticPlayer() {
        URI launch = URI.create("https://staff-staging.enthusia.info/launch?t=channel");

        ModerationPreviewLauncherPresentation.Rendered rendered = presentation.renderChannel(
                Optional.of(launch), STAFF_CHAT);
        MessageEmbed embed = rendered.embed();

        assertEquals("Moderation Workspace", embed.getTitle());
        assertNull(embed.getDescription());
        assertNull(embed.getThumbnail());
        assertEquals(STAFF_CHAT, fieldValue(embed, "Channel"));
        assertFalse(embed.toData().toString().contains("@"));
    }

    private static String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .map(MessageEmbed.Field::getValue)
                .findFirst()
                .orElseThrow();
    }
}
