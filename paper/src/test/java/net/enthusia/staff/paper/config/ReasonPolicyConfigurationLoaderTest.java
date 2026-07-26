package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class ReasonPolicyConfigurationLoaderTest {
    @Test
    void loadsTheCompleteDefaultCatalogAndCriticalPolicyShapes() {
        ReasonPolicyConfigurationLoader.LoadedPolicies loaded = loadDefaults();
        Map<String, ReasonPolicy> policies = loaded.policies().stream()
                .collect(Collectors.toMap(ReasonPolicy::id, Function.identity()));

        assertEquals("2026-07-26.1", loaded.version());
        assertEquals(84, policies.size());
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
    void rejectsConfigurationThatMakesPunishmentsPrivateWithoutStaffReview() throws Exception {
        String source;
        try (InputStream input = ReasonPolicyConfigurationLoaderTest.class.getClassLoader()
                .getResourceAsStream("reason-policies.yml")) {
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

    private static ReasonPolicyConfigurationLoader.LoadedPolicies loadDefaults() {
        InputStream input = ReasonPolicyConfigurationLoaderTest.class.getClassLoader()
                .getResourceAsStream("reason-policies.yml");
        return new ReasonPolicyConfigurationLoader().load(input, "reason-policies.yml");
    }
}
