package net.enthusia.staff.discordbot;

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
    private static final String LIVE_CONTEXT_PAGE_POLICY_SCRIPT =
            "/moderation-preview/live-context-page-policy.js";
    private static final String LIVE_CONTEXT_PAGINATION_SCRIPT =
            "/moderation-preview/live-context-pagination.js";
    private static final String LIVE_LOADING_SCRIPT = "/moderation-preview/live-loading.js";
    private static final String REAL_POLICY_SCRIPT = "/moderation-preview/real-policy.js";
    private static final String LIVE_ENHANCEMENTS_SCRIPT = "/moderation-preview/live-enhancements.js";
    private static final List<String> RESOURCES = List.of(
            "/moderation-preview/index.html",
            "/moderation-preview/app.css",
            "/moderation-preview/live.css",
            MODEL_SCRIPT,
            APP_SCRIPT,
            WORKFLOW_SCRIPT,
            REVIEW_SCRIPT,
            REAL_DATA_SCRIPT,
            DIRECT_READ_SCRIPT,
            LIVE_CONTEXT_PAGE_POLICY_SCRIPT,
            LIVE_CONTEXT_PAGINATION_SCRIPT,
            LIVE_LOADING_SCRIPT,
            REAL_POLICY_SCRIPT,
            LIVE_ENHANCEMENTS_SCRIPT);
    private static final List<String> SCRIPTS = List.of(
            MODEL_SCRIPT, APP_SCRIPT, WORKFLOW_SCRIPT, REVIEW_SCRIPT,
            REAL_DATA_SCRIPT, DIRECT_READ_SCRIPT, LIVE_CONTEXT_PAGE_POLICY_SCRIPT,
            LIVE_CONTEXT_PAGINATION_SCRIPT, LIVE_LOADING_SCRIPT, REAL_POLICY_SCRIPT,
            LIVE_ENHANCEMENTS_SCRIPT);

    @Test
    void everyModerationWorkspaceResourceIsPackaged() {
        for (String resource : RESOURCES) {
            assertNotNull(getClass().getResource(resource), resource);
        }
    }

    @Test
    void pageLoadsSplitScriptsInDependencyOrderWithoutDiagnosticBanner() throws IOException {
        String html = resourceText("/moderation-preview/index.html");

        assertOrdered(html,
                "/assets/model.js",
                "/assets/app.js",
                "/assets/workflow.js",
                "/assets/review.js",
                "/assets/real-data.js",
                "/assets/direct-read.js",
                "/assets/live-context-page-policy.js",
                "/assets/live-context-pagination.js",
                "/assets/live-loading.js",
                "/assets/real-policy.js",
                "/assets/live-enhancements.js");
        assertTrue(html.contains("/assets/live.css"));
        assertFalse(html.contains("STAGING PREVIEW"));
        assertTrue(html.contains("Live data"));
        assertFalse(html.contains("Sample case"));
        assertFalse(html.contains("Preview scenario"));
    }

    @Test
    void realDataAdapterUsesProtectedReadEndpointsAndTruthfulFailureState() throws IOException {
        String adapter = resourceText(REAL_DATA_SCRIPT);

        assertTrue(adapter.contains("requestDirectModerationRead('/api/bootstrap'"));
        assertTrue(adapter.contains("requestDirectModerationRead('/api/messages'"));
        assertTrue(adapter.contains("Read data unavailable"));
        assertTrue(adapter.contains("Text content unavailable from Discord"));
        assertFalse(adapter.contains("sample-river-ash"));
    }

    @Test
    void liveIdentityCardsUseResolvedMinecraftAndDiscordProfileFields() throws IOException {
        String enhancements = resourceText(LIVE_ENHANCEMENTS_SCRIPT);

        assertTrue(enhancements.contains("identity.avatarUrl"));
        assertTrue(enhancements.contains("identity.serverName"));
        assertTrue(enhancements.contains("identity.globalName"));
        assertTrue(enhancements.contains("account.username"));
        assertTrue(enhancements.contains("account.skinTextureUrl"));
        assertTrue(enhancements.contains("['Username', username]"));
        assertTrue(enhancements.contains("['Display name', discordName]"));
        assertTrue(enhancements.contains("['UUID', account.playerId]"));
    }

    @Test
    void liveMessagesRenderDiscordFormattingThroughSafeDomNodes() throws IOException {
        String enhancements = resourceText(LIVE_ENHANCEMENTS_SCRIPT);

        assertTrue(enhancements.contains("discordMessageContentNode(message.text)"));
        assertTrue(enhancements.contains("discord-heading"));
        assertTrue(enhancements.contains("discord-inline-code"));
        assertTrue(enhancements.contains("document.createTextNode"));
    }

    @Test
    void directReadAdapterUsesOnlyMintedOneUseProofsAndPinnedTunnelOrigin() throws IOException {
        String adapter = resourceText(DIRECT_READ_SCRIPT);

        assertTrue(adapter.contains("https://moderation-read-staging.enthusia.info"));
        assertTrue(adapter.contains("DIRECT_PROOF_PATHS = new Set(['/api/bootstrap', '/api/messages'])"));
        assertTrue(adapter.contains("if (!DIRECT_PROOF_PATHS.has(proofPath))"));
        assertTrue(adapter.contains("fetch('/api/bootstrap'"));
        assertTrue(adapter.contains("fetch('/api/messages'"));
        assertTrue(adapter.contains("fetch('https://moderation-read-staging.enthusia.info/v1/moderation/bootstrap'"));
        assertTrue(adapter.contains("fetch('https://moderation-read-staging.enthusia.info/v1/moderation/messages'"));
        assertFalse(adapter.contains("fetch(`${DIRECT_READ_ORIGIN}${proof.path}`"));
        assertTrue(adapter.contains("X-Enthusia-Read-Timestamp"));
        assertTrue(adapter.contains("X-Enthusia-Read-Nonce"));
        assertTrue(adapter.contains("X-Enthusia-Read-Signature"));
        assertTrue(adapter.contains("credentials:'omit'"));
        assertFalse(adapter.contains("loadSession ="));
        assertFalse(adapter.contains("loadMessageRequest ="));
        assertFalse(adapter.contains("channelFilterNode ="));
        assertFalse(adapter.contains("READ_API_SIGNING_KEY_HEX"));
        assertFalse(adapter.contains("ENTHUSIA_STAFF_BOT_TOKEN"));
        assertFalse(adapter.contains("workers.dev"));
    }

    @Test
    void directReadFailureStateIsSanitizedAndPersistent() throws IOException {
        String adapter = resourceText(DIRECT_READ_SCRIPT);

        assertTrue(adapter.contains("response.status === 403"));
        assertTrue(adapter.contains("response.status === 503"));
        assertTrue(adapter.contains("Access denied · staff authority not verified"));
        assertTrue(adapter.contains("Backend unavailable · read source failed"));
        assertTrue(adapter.contains("Read transport unavailable"));
        assertTrue(adapter.contains("identity, {status:failure.status"));
        assertFalse(adapter.contains("db.password"));
        assertFalse(adapter.contains("authority.secret"));
        assertFalse(adapter.contains("BOT_TOKEN"));
        assertFalse(adapter.contains("console.log"));
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
}
