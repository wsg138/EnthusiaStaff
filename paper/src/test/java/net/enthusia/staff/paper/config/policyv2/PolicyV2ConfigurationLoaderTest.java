package net.enthusia.staff.paper.config.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2ConfigurationLoaderTest {
    private static final String POLICY_ONE = "policy.1";
    private static final String EXAMPLE_DISPLAY_NAME = "Configured Example";

    private final PolicyV2ConfigurationLoader loader = new PolicyV2ConfigurationLoader();

    @Test
    void validLoadBuildsFullyValidatedW1Snapshot() {
        PolicyV2Configuration loaded = load(validConfiguration("shadow", POLICY_ONE, EXAMPLE_DISPLAY_NAME));

        assertEquals(PolicyV2FeatureMode.SHADOW, loaded.mode());
        assertEquals(POLICY_ONE, loaded.activeVersion());
        assertEquals(1, loaded.snapshots().size());
        var offense = loaded.activeSnapshot().offenses().getFirst();
        assertEquals("chat.example", offense.id());
        assertEquals(EXAMPLE_DISPLAY_NAME, offense.displayName());
        assertEquals(Duration.ofDays(30), offense.historyPolicy().decayPolicy().halfLife());
        assertEquals(Duration.ofDays(120), offense.historyPolicy().decayPolicy().patternHalfLife());
        PolicyAction.Exact exact = assertInstanceOf(PolicyAction.Exact.class, offense.rules().getFirst().action());
        assertEquals(SanctionType.WARNING, exact.sanctions().getFirst().type());
    }

    @Test
    void legacyExponentialConfigDefaultsPatternHalfLifeToDirectHalfLife() {
        String legacy = validConfiguration("shadow", POLICY_ONE, EXAMPLE_DISPLAY_NAME)
                .replaceFirst("(?m)^[ \\t]*pattern-half-life: 120d\\R", "");

        var decay = load(legacy).activeSnapshot().offenses().getFirst().historyPolicy().decayPolicy();

        assertEquals(decay.halfLife(), decay.patternHalfLife());
    }

    @Test
    void nonDecayingConfigRejectsPatternHalfLife() {
        String invalid = validConfiguration("shadow", POLICY_ONE, EXAMPLE_DISPLAY_NAME)
                .replace("mode: exponential", "mode: non-decaying")
                .replaceFirst("(?m)^[ \\t]*half-life: 30d\\R", "")
                .replaceFirst("(?m)^[ \\t]*repeat-half-life-increase-per-prior: 0.25\\R", "")
                .replaceFirst("(?m)^[ \\t]*maximum-half-life-multiplier: 2.0\\R", "");

        assertThrows(PolicyV2ConfigurationException.class, () -> load(invalid));
    }

    @Test
    void exactWithApprovalParsesFixedSanctionAndMinimumRank() {
        String yaml = validConfiguration("shadow", POLICY_ONE, EXAMPLE_DISPLAY_NAME)
                .replace(
                        "type: exact\n"
                                + "                              sanctions:",
                        "type: exact-with-approval\n"
                                + "                              minimum-rank: admin\n"
                                + "                              sanctions:"
                );

        PolicyAction.ExactWithApproval action = assertInstanceOf(
                PolicyAction.ExactWithApproval.class,
                load(yaml).activeSnapshot().offenses().getFirst().rules().getFirst().action()
        );

        assertEquals(StaffRank.ADMIN, action.minimumRank());
        assertEquals(SanctionType.WARNING, action.sanctions().getFirst().type());
    }

    @Test
    void exactWithApprovalRequiresMinimumRank() {
        String invalid = validConfiguration("shadow", POLICY_ONE, EXAMPLE_DISPLAY_NAME)
                .replace("type: exact", "type: exact-with-approval");

        assertThrows(PolicyV2ConfigurationException.class, () -> load(invalid));
    }

    @Test
    void invalidWholeSnapshotIsRejectedBeforePublication() {
        String invalid = validConfiguration("shadow", POLICY_ONE, EXAMPLE_DISPLAY_NAME)
                .replace("chat.example: 1.0", "missing.offense: 1.0");

        PolicyV2ConfigurationException failure = assertThrows(
                PolicyV2ConfigurationException.class,
                () -> load(invalid)
        );

        assertTrue(failure.getMessage().contains("unknown offense"));
    }

    @Test
    void duplicateVersionEntriesAreRejected() {
        String single = versionBlock(POLICY_ONE, "First");
        String yaml = """
                schema-version: 1
                mode: disabled
                active-version: policy.1
                versions:
                """ + single + single.replace("First", "Second");

        assertThrows(PolicyV2ConfigurationException.class, () -> load(yaml));
    }

    @Test
    void unknownFieldsAreRejectedInsteadOfSilentlyIgnored() {
        String yaml = validConfiguration("disabled", POLICY_ONE, EXAMPLE_DISPLAY_NAME)
                .replace("mode: disabled", "mode: disabled\nowner-threshold: 7");

        assertThrows(PolicyV2ConfigurationException.class, () -> load(yaml));
    }

    private PolicyV2Configuration load(String value) {
        return loader.load(
                new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)),
                "test-policy-v2.yml"
        );
    }

    static String validConfiguration(String mode, String version, String displayName) {
        return """
                schema-version: 1
                mode: %s
                active-version: %s
                versions:
                %s
                """.formatted(mode, version, versionBlock(version, displayName));
    }

    static String versionBlock(String version, String displayName) {
        return """
                  - version: %s
                    offenses:
                      - offense-id: chat.example
                        display-name: "%s"
                        navigation-group-id: chat-spam
                        attributes:
                          - attribute-id: severity
                            kind: enum
                            required: true
                            allowed-values: [low, high]
                        history:
                          relationships:
                            chat.example: 1.0
                          decay:
                            mode: exponential
                            half-life: 30d
                            pattern-half-life: 120d
                            repeat-half-life-increase-per-prior: 0.25
                            maximum-half-life-multiplier: 2.0
                        resolution-rules:
                          - rule-id: base
                            when:
                              attributes:
                                severity: [high]
                              history:
                                minimum-inclusive: 0
                            action:
                              type: exact
                              sanctions:
                                - type: warning
                                  duration: instant
                            remedies:
                              - remedy-id: remove-content
                                type: remove-content
                                description: "Example only"
                """.formatted(version, displayName);
    }
}
