package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1BehavioralHistorySource;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import org.junit.jupiter.api.Test;

class PolicyV2StoreAdapterTest {
    private static final UUID TARGET = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-07T05:00:00Z");

    @Test
    void resolverHistoryUsesCompletenessSafeStoreContract() {
        List<BehavioralHistoryEntry> expected = IntStream.range(0, 205)
                .mapToObj(PolicyV2StoreAdapterTest::entry)
                .toList();
        AtomicBoolean boundedCalled = new AtomicBoolean();
        PolicyV2Store store = proxyStore(expected, boundedCalled);
        PolicyV2StoreAdapter adapter = new PolicyV2StoreAdapter(() -> store);

        assertEquals(expected, adapter.completeHistory(TARGET, NOW));
        assertFalse(boundedCalled.get());
    }

    @Test
    void resolverHistoryMergesMappedPolicyV1CasesWithNativeV2HistoryDeterministically() {
        BehavioralHistoryEntry nativeV2 = new BehavioralHistoryEntry(
                "V2CASE0000000001",
                NOW.minusSeconds(20),
                "cheating.freecam",
                "cheating.freecam",
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
        PolicyV2Store store = proxyStore(List.of(nativeV2), new AtomicBoolean());
        PolicyV1BehavioralHistorySource legacy = (subjectId, asOf) -> List.of(
                new PolicyV1BehavioralHistorySource.LegacyFinding(
                        "V1XRAY0000000001",
                        NOW.minusSeconds(30),
                        "cheating.xray-esp"
                ),
                new PolicyV1BehavioralHistorySource.LegacyFinding(
                        "V1VPN00000000001",
                        NOW.minusSeconds(10),
                        "account.unapproved-vpn"
                )
        );
        PolicyV2StoreAdapter adapter = new PolicyV2StoreAdapter(() -> store, () -> legacy);

        List<BehavioralHistoryEntry> combined = adapter.completeHistory(TARGET, NOW);

        assertEquals(2, combined.size());
        assertEquals("v1:V1XRAY0000000001", combined.get(0).caseId());
        assertEquals("cheating.xray-esp", combined.get(0).effectiveOffenseId());
        assertEquals(nativeV2, combined.get(1));
    }

    private static PolicyV2Store proxyStore(
            List<BehavioralHistoryEntry> complete,
            AtomicBoolean boundedCalled
    ) {
        return (PolicyV2Store) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{PolicyV2Store.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "completeHistory" -> complete;
                    case "history" -> {
                        boundedCalled.set(true);
                        throw new AssertionError("resolver must not call bounded history");
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static BehavioralHistoryEntry entry(int index) {
        return new BehavioralHistoryEntry(
                "H%015d".formatted(index),
                NOW.minusSeconds(205L - index),
                "chat.spam",
                "chat.spam",
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
    }
}
