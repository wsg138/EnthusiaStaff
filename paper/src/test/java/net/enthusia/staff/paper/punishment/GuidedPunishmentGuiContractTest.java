package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuidedPunishmentGuiContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment", file));
    }

    @Test
    void punishUsesGuidedQuestionsButPreservesFilteredExpertFlows() throws Exception {
        String controller = source("PunishmentGuiController.java");
        assertTrue(controller.contains("\"punish\".equalsIgnoreCase(state.commandName())"));
        assertTrue(controller.contains("GuidedIncidentWizard.start(categoryId)"));
        assertTrue(controller.contains("new PunishmentGuiState.Reasons("));
        assertTrue(controller.contains("GuidedIncidentWizard.Question prompt"));
    }

    @Test
    void guidedPreviewHasNoDirectPunishmentSubmission() throws Exception {
        String controller = source("PunishmentGuiController.java");
        String guided = controller.substring(controller.indexOf("private void guidedClick("),
                controller.indexOf("private void reasonClick("));
        assertTrue(guided.contains("state.interview().answer("));
        assertTrue(guided.contains("state.interview().back()"));
        assertTrue(guided.contains("PunishmentGuiRenderer.SEARCH_SLOT"));
        assertTrue(!guided.contains("confirmRouted("));
        assertTrue(!guided.contains("PunishmentDraftWorkflow"));
        String renderer = source("PunishmentGuiRenderer.java");
        assertTrue(renderer.contains("Shadow preview: no punishment is applied."));
        assertTrue(renderer.contains("NOT an approved punishment recommendation."));
        assertTrue(renderer.contains("GuidedRemedyPreview.calculate("));
    }
}
