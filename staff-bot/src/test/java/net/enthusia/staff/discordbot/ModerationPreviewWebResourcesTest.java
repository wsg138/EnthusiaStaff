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
    private static final String MODEL_SCRIPT = "/moderation-preview/model.js";
    private static final String APP_SCRIPT = "/moderation-preview/app.js";
    private static final String WORKFLOW_SCRIPT = "/moderation-preview/workflow.js";
    private static final String REVIEW_SCRIPT = "/moderation-preview/review.js";
    private static final String REAL_DATA_SCRIPT = "/moderation-preview/real-data.js";
    private static final String DIRECT_READ_SCRIPT = "/moderation-preview/direct-read.js";
    private static final String REAL_POLICY_SCRIPT = "/moderation-preview/real-policy.js";
    private static final List<String> RESOURCES = List.of(
            "/moderation-preview/index.html",
            "/moderation-preview/app.css",
            MODEL_SCRIPT,
            APP_SCRIPT,
            WORKFLOW_SCRIPT,
            REVIEW_SCRIPT,
            REAL_DATA_SCRIPT,
            DIRECT_READ_SCRIPT,
            REAL_POLICY_SCRIPT);
    private static final List<String> SCRIPTS = List.of(
            MODEL_SCRIPT, APP_SCRIPT, WORKFLOW_SCRIPT, REVIEW_SCRIPT,
            REAL_DATA_SCRIPT, DIRECT_READ_SCRIPT, REAL_POLICY_SCRIPT);

    @Test
    void everyModerationWorkspaceResourceIsPackaged() {
        for (String resource : RESOURCES) {
            assertNotNull(getClass().getResource(resource), resource);
        }
    }

    @Test
    void pageLoadsSplitScriptsInDependencyOrderAndUsesOneStagingIndicator() throws IOException {
        String html = resourceText("/moderation-preview/index.html");

        assertOrdered(html,
                "/assets/model.js",
                "/assets/app.js",
                "/assets/workflow.js",
                "/assets/review.js",
                "/assets/real-data.js",
                "/assets/direct-read.js",
                "/assets/live-loading.js",
                "/assets/real-policy.js");
        assertEquals(1, occurrences(html, "STAGING PREVIEW"));
        assertTrue(html.contains("Live data"));
        assertFalse(html.contains("Sample case"));
        assertFalse(html.contains("Preview scenario"));
    }

    @Test
    void realDataAdapterUsesProtectedReadEndpointsAndTruthfulFailureState() throws IOException {
        String adapter = resourceText(REAL_DATA_SCRIPT);

        assertTrue(adapter.contains("fetch('/api/bootstrap'"));
        assertTrue(adapter.contains("fetch('/api/messages', {method:'POST'"));
        assertTrue(adapter.contains("Read data unavailable"));
        assertTrue(adapter.contains("Text content unavailable from Discord"));
        assertFalse(adapter.contains("sample-river-ash"));
    }

    @Test
    void directReadAdapterUsesOnlyMintedOneUseProofsAndPinnedTunnelOrigin() throws IOException {
        String adapter = resourceText(DIRECT_READ_SCRIPT);

        assertTrue(adapter.contains("https://moderation-read-staging.enthusia.info"));
        assertTrue(adapter.contains("fetch('/api/bootstrap'"));
        assertTrue(adapter.contains("fetch('/api/messages'"));
        assertTrue(adapter.contains("X-Enthusia-Read-Timestamp"));
        assertTrue(adapter.contains("X-Enthusia-Read-Nonce"));
        assertTrue(adapter.contains("X-Enthusia-Read-Signature"));
        assertTrue(adapter.contains("credentials:'omit'"));
        assertFalse(adapter.contains("READ_API_SIGNING_KEY_HEX"));
        assertFalse(adapter.contains("ENTHUSIA_STAFF_BOT_TOKEN"));
        assertFalse(adapter.contains("workers.dev"));
    }

    @Test
    void relevantHistoryUsesOnlyAuthoritativeSanctionFamily() throws IOException {
        String policy = resourceText(REAL_POLICY_SCRIPT);

        assertTrue(policy.contains("row.sanctionFamily"));
        assertTrue(policy.contains("LIVE_LADDER_FAMILIES.has(family)"));
        assertFalse(policy.contains("row.reason.includes"));
        assertFalse(policy.contains("Suggested for this sample"));
    }

    @Test
    void workspaceContainsRequiredEvidenceRestrictionAndReviewConcepts() throws IOException {
        String workspace = resourceText(APP_SCRIPT);
        String workflow = resourceText(WORKFLOW_SCRIPT);
        String review = resourceText(REVIEW_SCRIPT);

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
    void generatedUiUsesDomConstructionWithoutRawHtmlParsingSinks() throws IOException {
        for (String script : SCRIPTS) {
            String source = resourceText(script);
            assertFalse(source.contains(".innerHTML"), script);
            assertFalse(source.contains("DOMParser"), script);
            assertFalse(source.contains("insertAdjacentHTML"), script);
            assertFalse(source.contains("createContextualFragment"), script);
        }
        assertTrue(resourceText(MODEL_SCRIPT).contains("document.createElement"));
    }

    @Test
    void surroundingMessageContextRemainsAvailableWithoutDesktopOnlyClass() throws IOException {
        String workspace = resourceText(APP_SCRIPT);

        assertTrue(workspace.contains("contextMessage"));
        assertFalse(workspace.contains("context-button"));
        assertFalse(workspace.contains("Authority context"));
    }

    @Test
    void recommendationPathDoesNotSilentlyBecomeScenarioOverride() throws IOException {
        String workflow = resourceText(WORKFLOW_SCRIPT);
        int start = workflow.indexOf("function useRecommendation(custom)");
        int end = workflow.indexOf("function seedCustomScenario", start);

        assertTrue(start >= 0 && end > start);
        String recommendationPath = workflow.substring(start, end);
        assertTrue(recommendationPath.contains("if (custom) seedCustomScenario(w);"));
        assertFalse(recommendationPath.contains("state.scenario === 'restrict-one'"));
        assertFalse(recommendationPath.contains("state.scenario === 'custom'"));
    }

    @Test
    void exactTimestampsAndDateHeadingsUseOneExplicitTimeZone() throws IOException {
        String review = resourceText(REVIEW_SCRIPT);

        assertTrue(review.contains("DISPLAY_TIME_ZONE"));
        assertTrue(review.contains("displayDateKey(iso)"));
        assertTrue(review.contains("timeZone: DISPLAY_TIME_ZONE"));
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
        int position = text.indexOf(value);
        while (position >= 0) {
            count++;
            position = text.indexOf(value, position + value.length());
        }
        return count;
    }
}
