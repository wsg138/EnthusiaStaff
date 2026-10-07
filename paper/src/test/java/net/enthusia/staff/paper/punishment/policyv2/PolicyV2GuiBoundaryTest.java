package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PolicyV2GuiBoundaryTest {
    private static final Path RENDERER = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/policyv2/PolicyV2GuiRenderer.java"
    );
    private static final Path WORKFLOW = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/policyv2/PolicyV2ManualWorkflow.java"
    );
    private static final Path CONTROLLER = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/policyv2/PolicyV2GuiController.java"
    );
    private static final Path REGISTRAR = Path.of(
            "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
    );

    @Test
    void resultScreenKeepsShadowAndV1AuthorityBoundaryVisible() throws IOException {
        String source = Files.readString(RENDERER);

        assertTrue(source.contains("SHADOW — Policy v1 remains authoritative"));
        assertTrue(source.contains("Records a shadow evaluation only."));
        assertFalse(source.contains("matchedRuleId"));
        assertFalse(source.contains("relationshipWeight"));
        assertFalse(source.contains("totalContribution"));
        assertFalse(source.contains("caseId"));
    }

    @Test
    void workflowUsesW2ShadowPortAndNeverCreatesAuthoritativeCase() throws IOException {
        String source = Files.readString(WORKFLOW);

        assertTrue(source.contains("ShadowRecorder"));
        assertTrue(source.contains("submitShadow"));
        assertFalse(source.contains("createCase("));
        assertFalse(source.contains("PunishmentService"));
    }

    @Test
    void interactionControllerDrivesTheShadowWorkflowButIsNotLiveRegistered() throws IOException {
        String controller = Files.readString(CONTROLLER);
        String registrar = Files.readString(REGISTRAR);

        assertTrue(controller.contains("InventoryClickEvent"));
        assertTrue(controller.contains("AsyncChatEvent"));
        assertTrue(controller.contains("workflow.submitShadow("));
        assertTrue(controller.contains("Policy or history changed."));
        assertFalse(controller.contains("PunishmentService"));
        assertFalse(controller.contains("createCase("));
        assertFalse(registrar.contains("PolicyV2GuiController"));
    }
}
