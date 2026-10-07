package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1BehavioralHistorySource;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1HistoryCarryForward;
import org.junit.jupiter.api.Test;

class PolicyV1HistoryCarryForwardTest {
    private static final Pattern REASON_ID = Pattern.compile("(?m)^\\s*- id: ([^\\s#]+)\\s*$");

    private final PolicyV1HistoryCarryForward carryForward = new PolicyV1HistoryCarryForward();

    @Test
    void everyConfiguredPolicyV1ReasonHasAnExplicitCarryForwardDecision() throws IOException {
        String yaml;
        try (var stream = PolicyV1HistoryCarryForwardTest.class.getResourceAsStream("/reason-policies.yml")) {
            if (stream == null) {
                throw new AssertionError("reason-policies.yml is unavailable");
            }
            yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> configured = REASON_ID.matcher(yaml)
                .results()
                .map(match -> match.group(1))
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(configured, carryForward.decisions().keySet());
    }

    @Test
    void approvedMappingsCarryForwardAndAmbiguousReasonsStayOutOfBehavioralHistory() {
        var xray = carryForward.convert(new PolicyV1BehavioralHistorySource.LegacyFinding(
                "ABC1234567890123",
                Instant.parse("2026-01-01T00:00:00Z"),
                "cheating.xray-esp"
        )).orElseThrow();

        assertEquals("v1:ABC1234567890123", xray.caseId());
        assertEquals("cheating.xray-esp", xray.originalOffenseId());
        assertEquals("cheating.xray-esp", xray.effectiveOffenseId());

        assertTrue(carryForward.decision("politics.extreme").orElseThrow().mapped());
        assertEquals(
                "chat.sensitive-topic-public",
                carryForward.decision("politics.extreme").orElseThrow().v2OffenseId().orElseThrow()
        );

        for (String reason : Set.of(
                "account.unapproved-vpn",
                "identity.inappropriate-profile",
                "account.unsafe-download",
                "exploit.duplicated-possession-unclear",
                "market.compliance-failure",
                "reports.abusive-content"
        )) {
            assertFalse(carryForward.decision(reason).orElseThrow().mapped(), reason);
        }
    }

    @Test
    void unknownFuturePolicyV1ReasonFailsConservatively() {
        assertTrue(carryForward.decision("future.unknown-reason").isEmpty());
        assertTrue(carryForward.convert(new PolicyV1BehavioralHistorySource.LegacyFinding(
                "ABC1234567890999",
                Instant.parse("2026-01-01T00:00:00Z"),
                "future.unknown-reason"
        )).isEmpty());
    }
}
