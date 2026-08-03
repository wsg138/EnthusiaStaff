package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class ReasonPolicyConfigurationLoaderTest {
    private static final String POLICY_RESOURCE = "reason-policies.yml";
    private static final String CHAT_POLICY_ID = "chat.harassment";

    @Test
    void loadsTheCompleteDefaultCatalogAndCriticalPolicyShapes() {
        ReasonPolicyConfigurationLoader.LoadedPolicies loaded = loadDefaults();
        Map<String, ReasonPolicy> policies = loaded.policies().stream()
                .collect(Collectors.toMap(ReasonPolicy::id, Function.identity()));

        assertEquals("2026-07-26.1", loaded.version());
        assertEquals(84, policies.size());
        assertTrue(loaded.aliases().isEmpty());
        assertTrue(loaded.removedReasons().isEmpty());
        assertEquals(5, policies.get("harassment.sexual").steps().size());
        assertEquals(7, policies.get("content.explicit-sexual").steps().size());
        assertEquals(4, policies.get("cheating.polar.template").steps().size());
        assertTrue(policies.get("hate.full-slur-untargeted").automaticDetectionAllowed());
        assertTrue(policies.values().stream().allMatch(ReasonPolicy::publicByDefault));
        assertTrue(policies.get("exploit.illegal-duplication").confiscationAllowed());
        assertEquals(
                SanctionType.MARKET_BLACKLIST,
                policies.get("market.compliance-failure").steps().get(3).sanctions().get(1).type()
        );
    }

    @Test
    void loadsExplicitAliasesAndReadableRemovedReasons() {
        ReasonPolicyConfigurationLoader.LoadedPolicies loaded = load("""
                version: "2026-08-02.1"
                defaults:
                  decay-eligible: true
                  public-default: true
                  reportable: true
                  confiscation-options: false
                  required-rank: MOD
                  automatic-detection-eligible: false
                  alt-inheritance: ACTIVE_SANCTIONS
                aliases:
                  - id: chat.old-harassment
                    target: chat.harassment
                removed-reasons:
                  - id: chat.retired-abuse
                    family: chat
                    display-name: Retired abusive language
                reasons:
                  - id: chat.harassment
                    family: chat
                    display-name: Harassment
                    severity: 25
                    ladder:
                      - label: Warning
                        sanctions:
                          - type: WARNING
                            duration: instant
                """);

        assertEquals(Map.of("chat.old-harassment", CHAT_POLICY_ID), loaded.aliases());
        assertEquals(1, loaded.removedReasons().size());
        assertEquals("Retired abusive language", loaded.removedReasons().getFirst().publicReason());
        assertFalse(loaded.policies().stream().anyMatch(policy -> policy.id().equals("chat.retired-abuse")));
    }

    @Test
    void rejectsAliasTargetsThatAreMissingOrAnotherAlias() {
        String missingTarget = minimalConfiguration("""
                aliases:
                  - id: chat.old
                    target: chat.missing
                """);
        String chainedTarget = minimalConfiguration("""
                aliases:
                  - id: chat.old
                    target: chat.older
                  - id: chat.older
                    target: chat.harassment
                """);

        assertThrows(ConfigurationValidationException.class, () -> load(missingTarget));
        assertThrows(ConfigurationValidationException.class, () -> load(chainedTarget));
    }

    @Test
    void rejectsAliasOverlapWithRemovedOrActiveReasons() {
        String removedOverlap = minimalConfiguration("""
                aliases:
                  - id: chat.retired
                    target: chat.harassment
                removed-reasons:
                  - id: chat.retired
                    family: chat
                    display-name: Retired reason
                """);
        String activeOverlap = minimalConfiguration("""
                aliases:
                  - id: chat.harassment
                    target: chat.harassment
                """);

        assertThrows(ConfigurationValidationException.class, () -> load(removedOverlap));
        assertThrows(ConfigurationValidationException.class, () -> load(activeOverlap));
    }

    @Test
    void rejectsMalformedAliasIdentifiersDuringLoad() {
        String malformed = minimalConfiguration("""
                aliases:
                  - id: Chat.Old
                    target: chat.harassment
                """);

        assertThrows(ConfigurationValidationException.class, () -> load(malformed));
    }

    @Test
    void rejectsConfigurationThatMakesPunishmentsPrivateWithoutStaffReview() throws Exception {
        String source;
        try (InputStream input = policyResource()) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceFirst("public-default: true", "public-default: false");
        }

        assertThrows(
                ConfigurationValidationException.class,
                () -> new ReasonPolicyConfigurationLoader().load(
                        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                        "private-default.yml"
                )
        );
    }

    private static String minimalConfiguration(String metadata) {
        return """
                version: "2026-08-02.1"
                defaults:
                  decay-eligible: true
                  public-default: true
                  reportable: true
                  confiscation-options: false
                  required-rank: MOD
                  automatic-detection-eligible: false
                  alt-inheritance: ACTIVE_SANCTIONS
                %s
                reasons:
                  - id: chat.harassment
                    family: chat
                    display-name: Harassment
                    severity: 25
                    ladder:
                      - label: Warning
                        sanctions:
                          - type: WARNING
                            duration: instant
                """.formatted(metadata.stripIndent());
    }

    private static ReasonPolicyConfigurationLoader.LoadedPolicies load(String source) {
        return new ReasonPolicyConfigurationLoader().load(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                "test-policies.yml"
        );
    }

    private static ReasonPolicyConfigurationLoader.LoadedPolicies loadDefaults() {
        return new ReasonPolicyConfigurationLoader().load(policyResource(), POLICY_RESOURCE);
    }

    private static InputStream policyResource() {
        return Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(POLICY_RESOURCE),
                POLICY_RESOURCE + " must exist on the test classpath"
        );
    }
}
