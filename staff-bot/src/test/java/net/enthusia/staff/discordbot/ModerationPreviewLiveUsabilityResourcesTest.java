package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationPreviewLiveUsabilityResourcesTest {
    private static final List<String> ASSETS = List.of(
            "/moderation-preview/live-review-hardening.js",
            "/moderation-preview/live-shell-usability.js",
            "/moderation-preview/live-message-usability.js",
            "/moderation-preview/live-record-usability.js");

    @Test
    void usabilityAssetsArePackagedAndLoadedAfterLiveEnhancements() throws IOException {
        String html = resourceText("/moderation-preview/index.html");
        int previous = html.indexOf("/assets/live-enhancements.js");
        assertTrue(previous >= 0);
        for (String resource : ASSETS) {
            assertNotNull(getClass().getResource(resource), resource);
            String publicPath = resource.replace("/moderation-preview/", "/assets/");
            int current = html.indexOf(publicPath);
            assertTrue(current > previous, publicPath);
            previous = current;
        }
    }

    @Test
    void usabilityAssetsAvoidRawHtmlParsingSinks() throws IOException {
        for (String resource : ASSETS) {
            String source = resourceText(resource);
            assertFalse(source.contains(".innerHTML"), resource);
            assertFalse(source.contains("DOMParser"), resource);
            assertFalse(source.contains("insertAdjacentHTML"), resource);
            assertFalse(source.contains("createContextualFragment"), resource);
        }
    }

    private String resourceText(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
