package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationPreviewWebResourcesTest {
    private static final List<String> RESOURCES = List.of(
            "/moderation-preview/index.html",
            "/moderation-preview/app.css",
            "/moderation-preview/model.js",
            "/moderation-preview/app.js",
            "/moderation-preview/workflow.js",
            "/moderation-preview/review.js");
    private static final List<String> SCRIPTS = List.of(
            "/moderation-preview/model.js",
            "/moderation-preview/app.js",
            "/moderation-preview/workflow.js",
            "/moderation-preview/review.js");

    @Test
    void everyModerationWorkspaceResourceIsPackaged() {
        for (String resource : RESOURCES) {
            assertNotNull(getClass().getResource(resource), resource);
        }
    }

    @Test
    void pageLoadsSplitScriptsInDependencyOrderAndUsesOneStagingIndicator() throws IOException {
        String html = resourceText("/moderation-preview/index.html");

        assertOrdered(html, "/assets/model.js", "/assets/app.js", "/assets/workflow.js", "/assets/review.js");
        assertEquals(1, occurrences(html, "STAGING PREVIEW"));
        assertTrue(html.contains("Sample case"));
        assertFalse(html.contains("Preview scenario"));
    }

    @Test
    void workspaceContainsRequiredEvidenceRestrictionAndReviewConcepts() throws IOException {
        String workspace = resourceText("/moderation-preview/app.js");
        String workflow = resourceText("/moderation-preview/workflow.js");
        String review = resourceText("/moderation-preview/review.js");

        assertTrue(workspace.contains("Add to Evidence"));
        assertTrue(workspace.contains("Delete on Confirm"));
        assertTrue(workflow.contains("Read only"));
        assertTrue(workflow.contains("No access"));
        assertTrue(workflow.contains("Custom override"));
        assertTrue(workflow.contains("Relevant history"));
        assertTrue(review.contains("Messages to delete"));
        assertTrue(review.contains("Simulation complete"));
        assertTrue(review.contains("No live moderation action was performed."));
    }

    @Test
    void generatedMarkupAvoidsDirectInnerHtmlSinks() throws IOException {
        for (String script : SCRIPTS) {
            assertFalse(resourceText(script).contains(".innerHTML"), script);
        }
        assertTrue(resourceText("/moderation-preview/model.js").contains("new DOMParser()"));
    }

    @Test
    void surroundingMessageContextRemainsAvailableWithoutDesktopOnlyClass() throws IOException {
        String workspace = resourceText("/moderation-preview/app.js");

        assertTrue(workspace.contains("data-context-message"));
        assertFalse(workspace.contains("context-button"));
        assertFalse(workspace.contains("Authority context"));
    }

    @Test
    void recommendationPathDoesNotSilentlyBecomeScenarioOverride() throws IOException {
        String workflow = resourceText("/moderation-preview/workflow.js");
        int start = workflow.indexOf("function useRecommendation(custom)");
        int end = workflow.indexOf("function seedCustomScenario", start);

        assertTrue(start >= 0 && end > start);
        String recommendationPath = workflow.substring(start, end);
        assertTrue(recommendationPath.contains("if (custom) seedCustomScenario(w);"));
        assertFalse(recommendationPath.contains("state.scenario==='restrict-one'"));
        assertFalse(recommendationPath.contains("state.scenario==='custom'"));
    }

    private String resourceText(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertOrdered(String text, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = text.indexOf(value);
            assertTrue(current > previous, () -> value + " must appear in dependency order");
            previous = current;
        }
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int position = 0;
        while ((position = text.indexOf(value, position)) >= 0) {
            count++;
            position += value.length();
        }
        return count;
    }
}
