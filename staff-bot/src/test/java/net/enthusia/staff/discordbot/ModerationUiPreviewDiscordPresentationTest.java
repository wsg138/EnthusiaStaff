package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModerationUiPreviewDiscordPresentationTest {
    private final ModerationUiPreviewDiscordPresentation presentation = new ModerationUiPreviewDiscordPresentation();

    @Test
    void overviewFitsDiscordComponentLimitsAndIncludesNavigation() {
        ModerationUiPreviewModel.Snapshot snapshot = new ModerationUiPreviewModel.Snapshot(
                "abcdefghijklmnop",
                0,
                ModerationUiPreviewModel.State.initial()
        );

        ModerationUiPreviewDiscordPresentation.Rendered rendered = presentation.render(snapshot);

        assertEquals(2, rendered.rows().size());
        assertEquals(5, rendered.rows().getFirst().getComponents().size());
        assertTrue(rendered.embed().getFooter().getText().contains("No moderation action is possible"));
        rendered.rows().stream()
                .flatMap(row -> row.getComponents().stream())
                .filter(component -> component.getCustomId() != null)
                .forEach(component -> assertTrue(component.getCustomId().length() <= 100));
    }

    @Test
    void commandIsDedicatedPreviewCommand() {
        assertEquals("moderate-preview", JdaModerationUiPreviewListener.command().getName());
    }
}
