package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.enthusia.staff.domain.guided.GuidedIncidentWizard;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
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

    @Test
    void guidedResultShowsFactsAndFinalReviewRatherThanUnapprovedScore() throws Exception {
        String renderer = source("PunishmentGuiRenderer.java");
        assertFalse(renderer.contains("Preliminary incident impact:"));
        assertTrue(renderer.contains("Incident answers recorded for assessment."));
        assertTrue(renderer.contains("Exact sanction and duration appear in final review."));
        assertTrue(renderer.contains("Uncertain incidents require individual staff review."));
        assertTrue(renderer.contains("finding.needsReview() || finding.candidateId() == null"));
        assertTrue(renderer.contains("No additional factors selected."));
        assertTrue(renderer.contains("Relevant punishment history is checked automatically."));
    }

    @Test
    void changedPolicyHidesSubmissionAndBlocksStaleDraftClick() throws Exception {
        assertTrue(PunishmentGuiRenderer.reviewPolicyCurrent("policy-1", "policy-1"));
        assertFalse(PunishmentGuiRenderer.reviewPolicyCurrent("policy-1", "policy-2"));
        assertFalse(PunishmentGuiRenderer.reviewPolicyCurrent(null, "policy-2"));
        String renderer = source("PunishmentGuiRenderer.java");
        assertTrue(renderer.contains("Reassessment Required"));
        assertTrue(renderer.contains("Rules changed since this draft was prepared."));
        assertTrue(renderer.contains("Go back and assess against the current policy."));
        assertTrue(renderer.contains("Rules changed; reassessment required before submission"));
        String controller = source("PunishmentGuiController.java");
        String handler = controller.substring(controller.indexOf("private void reviewClick("),
                controller.indexOf("private void historyClick("));
        assertTrue(handler.contains("PunishmentGuiRenderer.reviewPolicyCurrent("));
        assertTrue(handler.contains("Go back and reassess before submitting."));
        assertTrue(handler.indexOf("PunishmentGuiRenderer.reviewPolicyCurrent(")
                < handler.indexOf("confirm(viewer, actor, state)"));
    }

    @Test
    void finalGuidedReviewShowsFactsWithoutUnapprovedImpactScore() throws Exception {
        String renderer = source("PunishmentGuiRenderer.java");
        String review = renderer.substring(renderer.indexOf("renderGuidedReviewFactors("),
                renderer.indexOf("private void renderHistory("));
        assertFalse(review.contains("value.startsWith(" + '"' + "incidentImpact=" + '"' + ")"));
        assertTrue(review.contains("value.startsWith(" + '"' + "continued=" + '"' + ")"));
        String controller = source("PunishmentGuiController.java");
        assertTrue(controller.contains(" | incidentImpact="));
    }

    @Test
    void disabledGuidedModeBlocksAlreadyOpenQuestionnairesAndQueuedPreparation() throws Exception {
        String controller = source("PunishmentGuiController.java");
        assertTrue(controller.contains(
                "plugin.getConfig().getBoolean(\"guided-punishment.enabled\", false)"));
        String entry = controller.substring(controller.indexOf("private void categoryClick("),
                controller.indexOf("private void openGuidedExpertReasons("));
        assertTrue(entry.contains("&& guidedModeEnabled()"));
        assertTrue(entry.contains("if (!guidedModeEnabled())"));
        assertTrue(entry.contains("openState(viewer, categoriesState(state))"));

        String preparation = controller.substring(controller.indexOf("private void prepareGuided("),
                controller.indexOf("private static String guidedExplanation("));
        assertTrue(preparation.contains("if (!guidedModeEnabled())"));
        assertTrue(preparation.contains("Guided punishment mode was disabled during calculation."));
        assertTrue(preparation.lastIndexOf("if (!guidedModeEnabled())")
                < preparation.indexOf("workflow.prepare(proposal, mode.get())"));
        assertTrue(preparation.lastIndexOf("if (!guidedModeEnabled())")
                > preparation.indexOf("GuidedPolicyResolution.calculate("));
    }

    @Test
    void disablingGuidedModeLocksSavedReviewWhileExpertRoutesRemainAvailable() throws Exception {
        String controller = source("PunishmentGuiController.java");
        assertTrue(controller.contains(
                "renderer.render(state, actor, guidedModeEnabled())"));
        String resume = controller.substring(controller.indexOf("public void resume("),
                controller.indexOf("public void showPreparedDraft("));
        assertTrue(resume.contains(
                "isGuidedDraft(draft.internalExplanation()) && !guidedModeEnabled()"));

        String review = controller.substring(controller.indexOf("private void reviewClick("),
                controller.indexOf("private void historyClick("));
        assertTrue(review.contains(
                "isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()"));
        String reprepare = controller.substring(controller.indexOf("private void reprepare("),
                controller.indexOf("private void showPrepared("));
        assertTrue(reprepare.contains(
                "isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()"));
        String confirm = controller.substring(controller.indexOf("private void confirmOnce("),
                controller.indexOf("private void handleConfirmation("));
        assertTrue(confirm.contains(
                "isGuidedDraft(state.draft().internalExplanation()) && !guidedModeEnabled()"));
        assertTrue(confirm.contains("No sanction or approval request was submitted."));

        String renderer = source("PunishmentGuiRenderer.java");
        assertTrue(renderer.contains("Guided Mode Disabled"));
        assertTrue(renderer.contains("boolean guidedDisabled = guidedReview && !guidedModeEnabled"));
        assertTrue(renderer.contains("This saved draft cannot be submitted."));
        assertTrue(controller.contains("new PunishmentGuiState.Reasons("));
    }

    @Test
    void warningAndSeverityChoicesUseMeaningfulMaterials() {
        assertEquals(Material.RED_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "continued", "yes", 0));
        assertEquals(Material.LIME_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "continued", "no", 1));
        assertEquals(Material.LIGHT_GRAY_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "continued", "not-applicable", 2));
        assertEquals(Material.YELLOW_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "continued", "unknown", 3));
        assertEquals(Material.LIME_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "extent", "few", 0));
        assertEquals(Material.ORANGE_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "extent", "sustained", 1));
        assertEquals(Material.RED_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "extent", "large-scale", 2));
        assertEquals(Material.YELLOW_DYE, PunishmentGuiRenderer.guidedAnswerMaterial(
                "targeting", "unknown", 2));
    }

    @Test
    void warningChoicesExplainDefianceWithoutAssumingIt() {
        var plain = PlainTextComponentSerializer.plainText();
        assertEquals("A verified warning was ignored.", plain.serialize(
                PunishmentGuiRenderer.guidedAnswerLore("continued", "yes").getFirst()));
        assertEquals("They stopped after a warning; not defiance.", plain.serialize(
                PunishmentGuiRenderer.guidedAnswerLore("continued", "no").getFirst()));
        assertEquals("No warning was given; not defiance.", plain.serialize(
                PunishmentGuiRenderer.guidedAnswerLore("continued", "not-applicable").getFirst()));
        assertEquals("Warning status uncertain; do not assume defiance.", plain.serialize(
                PunishmentGuiRenderer.guidedAnswerLore("continued", "unknown").getFirst()));
    }
}
