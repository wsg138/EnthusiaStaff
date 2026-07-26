package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
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

        assertEquals("2026-07-22.1", loaded.version());
        assertEquals(84, policies.size());
        assertEquals(5, policies.get("harassment.sexual").steps().size());
        assertEquals(7, policies.get("content.explicit-sexual").steps().size());
        assertEquals(4, policies.get("cheating.polar.template").steps().size());
        assertTrue(policies.get("hate.full-slur-untargeted").automaticDetectionAllowed());
        assertFalse(policies.get("privacy.doxxing").publicByDefault());
        assertTrue(policies.get("exploit.illegal-duplication").confiscationAllowed());
        assertEquals(
                SanctionType.MARKET_BLACKLIST,
                policies.get("market.compliance-failure").steps().get(3).sanctions().get(1).type()
        );
    }

    private static ReasonPolicyConfigurationLoader.LoadedPolicies loadDefaults() {
        InputStream input = ReasonPolicyConfigurationLoaderTest.class.getClassLoader()
                .getResourceAsStream("reason-policies.yml");
        return new ReasonPolicyConfigurationLoader().load(input, "reason-policies.yml");
    }
}
