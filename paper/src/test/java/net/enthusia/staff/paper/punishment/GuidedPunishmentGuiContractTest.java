package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.enthusia.staff.domain.guided.GuidedIncidentWizard;
import org.junit.jupiter.api.Test;

class GuidedPunishmentGuiContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment", file));
    }

    @Test
    void punishUsesGuidedQuestionsButPreservesFilteredExpertFlows() throws Exception {
        String controller = source("PunishmentGuiController.java");
        assertTrue(controller.contains("PUNISH_COMMAND.equalsIgnoreCase(state.commandName())"));
        assertTrue(controller.contains("GuidedIncidentWizard.start(categoryId)"));
        assertTrue(controller.contains(
                "plugin.getConfig().getBoolean(\"guided-punishment.enabled\", false)"));
        assertTrue(controller.contains("new PunishmentGuiState.Reasons("));
        assertTrue(controller.contains("GuidedIncidentWizard.Question prompt"));
    }

    @Test
    void guidedProposalPassesThroughExistingDraftAuthorization() throws Exception {
        String controller = source("PunishmentGuiController.java");
        String guided = controller.substring(controller.indexOf("private void guidedClick("),
                controller.indexOf("private void reasonClick("));
        assertTrue(guided.contains("state.interview().answer("));
        assertTrue(guided.contains("state.interview().back()"));
        assertTrue(guided.contains("workflow.preview(readOnly, mode.get())"));
        assertTrue(guided.contains("GuidedPolicyResolution.calculate("));
        assertTrue(guided.contains("workflow.prepare(proposal, mode.get())"));
        assertTrue(!guided.contains("confirmRouted("));
        String renderer = source("PunishmentGuiRenderer.java");
        assertTrue(renderer.contains("Relevant punishment history is checked automatically."));
        assertTrue(renderer.contains("Opens final review; no punishment is applied yet."));
        assertTrue(!renderer.contains("GuidedRemedyPreview.calculate("));
    }

    @Test
    void firstAnsweredQuestionCanBeEditedWithBackInsteadOfLeavingInterview() {
        GuidedIncidentWizard initial = GuidedIncidentWizard.start("chat");
        assertTrue(PunishmentGuiController.guidedBackGoesToCategories(initial));
        GuidedIncidentWizard.Question first = initial.nextQuestion().orElseThrow();
        GuidedIncidentWizard answered = initial.answer(first.id(), first.choices().getFirst().id());
        assertFalse(PunishmentGuiController.guidedBackGoesToCategories(answered));
        assertEquals(first.id(), answered.back().nextQuestion().orElseThrow().id());
    }

    @Test
    void reviewShowsRationaleLockInsteadOfAnActionableSubmitButton() throws Exception {
        String renderer = source("PunishmentGuiRenderer.java");
        assertTrue(renderer.contains("Material.GRAY_CONCRETE, \"Staff Rationale Required\""));
        assertTrue(renderer.contains("PunishmentGuiController.guidedNeedsStaffRationale("));
        assertTrue(renderer.contains("Submission is blocked until you add a note."));
        assertTrue(renderer.contains("Incident answers remain preserved in the audit."));
    }

    @Test
    void categoryDirectionsDescribeBothGuidedAndLegacyModesAccurately() throws Exception {
        String renderer = source("PunishmentGuiRenderer.java");
        assertTrue(renderer.contains("Guided mode asks relevant questions when enabled."));
        assertTrue(renderer.contains("Click to continue with this category"));
        assertTrue(renderer.contains("Back · Categories"));
    }
}
