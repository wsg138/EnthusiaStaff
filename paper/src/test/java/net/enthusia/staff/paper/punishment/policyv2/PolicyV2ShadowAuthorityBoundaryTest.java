package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PolicyV2ShadowAuthorityBoundaryTest {
    private static final Path ENFORCEMENT = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/policyv2/PolicyV2ShadowEnforcementRuntime.java"
    );
    private static final Path OBSERVER = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/policyv2/PolicyV2ShadowComplianceListener.java"
    );
    private static final Path CONTROLLER = Path.of(
            "src/main/java/net/enthusia/staff/paper/punishment/policyv2/PolicyV2GuiController.java"
    );
    private static final Path REGISTRAR = Path.of(
            "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
    );

    @Test
    void shadowRuntimeCanObserveButHasNoPunitiveProviderEntryPoint() throws IOException {
        String source = Files.readString(ENFORCEMENT);

        assertTrue(source.contains("PolicyV2AccessCoordinator"));
        assertTrue(source.contains("PolicyV2CapabilityGate"));
        assertFalse(source.contains(".enforce("));
        assertFalse(source.contains("PolicyV2AssetRemedyAdapter"));
        assertFalse(source.contains("MarketIntegration"));
        assertFalse(source.contains("ReputationIntegration"));
    }

    @Test
    void complianceObserverNeverCancelsOrDeniesRuntimeActions() throws IOException {
        String source = Files.readString(OBSERVER);

        assertTrue(source.contains("VpnState.UNKNOWN"));
        assertTrue(source.contains("Scope.REPORT_SUBMISSION"));
        assertFalse(source.contains("setCancelled("));
        assertFalse(source.contains("kickPlayer("));
        assertFalse(source.contains(".ban("));
    }

    @Test
    void openShadowUiIsFencedEveryTimeTheFeatureIsDisabled() throws IOException {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("if (!enabled.getAsBoolean())"));
        assertTrue(source.contains("viewer.closeInventory()"));
        assertTrue(source.contains("Policy v2 shadow mode is disabled; no evaluation was recorded."));
    }

    @Test
    void punishRegistrationRemainsSeparateFromPolicyV2ShadowEntryPoint() throws IOException {
        String source = Files.readString(REGISTRAR);

        assertTrue(source.contains("PunishmentCommand command = new PunishmentCommand"));
        assertTrue(source.contains("PolicyV2ShadowAccess"));
        assertFalse(source.contains("new PolicyV2GuiController"));
    }
}
