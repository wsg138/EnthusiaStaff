package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModerationPreviewLauncherPresentationTest {
    @Test
    void launcherIsCompactAndWebFirst() {
        var rendered = new ModerationPreviewLauncherPresentation().render(
                Optional.of(URI.create("https://staff-preview.example.test/launch?t=abc")));

        assertEquals("Moderation · RiverAsh", rendered.embed().getTitle());
        assertEquals("STAGING PREVIEW", rendered.embed().getFooter().getText());
        assertEquals(3, rendered.embed().getFields().size());
        assertEquals(1, rendered.rows().size());
        assertEquals("Open Moderation Panel", rendered.rows().getFirst().getComponents().getFirst().getLabel());
        assertFalse(rendered.embed().getDescription().contains("fake"));
    }

    @Test
    void missingExternalDeploymentDoesNotCreateAnUnsafeFallbackUrl() {
        var rendered = new ModerationPreviewLauncherPresentation().render(Optional.empty());

        var button = rendered.rows().getFirst().getComponents().getFirst();
        assertTrue(button.isDisabled());
        assertEquals("Panel deployment required", button.getLabel());
    }
}
