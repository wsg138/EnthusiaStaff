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
