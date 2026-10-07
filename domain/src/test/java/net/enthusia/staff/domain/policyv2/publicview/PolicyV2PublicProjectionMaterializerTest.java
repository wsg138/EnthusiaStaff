package net.enthusia.staff.domain.policyv2.publicview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2PublicProjectionMaterializerTest {
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final String CASE_ID = "ABCDEFGHJKMNPQRS";

    @Test
    void publishesProjectedAllowlistedRecordWithCallerFence() {
        AtomicReference<PolicyV2Store.PublishProjectionRequest> captured = new AtomicReference<>();
        PolicyV2Store store = store(captured, null);
        PolicyV2PublicProjectionMaterializer materializer = new PolicyV2PublicProjectionMaterializer(store);

        PolicyV2PublicProjection result =
                materializer.materialize(source(3), 2, "publish:case:3", NOW.plusSeconds(10));

        assertEquals(3, result.revision());
        assertEquals(2, captured.get().expectedRevision());
        assertEquals("publish:case:3", captured.get().operationKey());
    }

    @Test
    void rejectsRevisionThatDoesNotAdvanceExactlyOnceBeforePersistence() {
        AtomicReference<PolicyV2Store.PublishProjectionRequest> captured = new AtomicReference<>();
        PolicyV2PublicProjectionMaterializer materializer =
                new PolicyV2PublicProjectionMaterializer(store(captured, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> materializer.materialize(source(4), 2, "bad-revision", NOW)
        );
        assertNull(captured.get());
    }

    @Test
    void persistenceConflictPropagatesWithoutFallbackMutation() {
        PolicyV2Store.Conflict conflict = new PolicyV2Store.Conflict("stale projection");
        PolicyV2PublicProjectionMaterializer materializer =
                new PolicyV2PublicProjectionMaterializer(store(new AtomicReference<>(), conflict));

        PolicyV2Store.Conflict thrown = assertThrows(
                PolicyV2Store.Conflict.class,
                () -> materializer.materialize(source(3), 2, "conflict", NOW)
        );
        assertEquals(conflict, thrown);
    }

    private static PolicyV2Store store(
            AtomicReference<PolicyV2Store.PublishProjectionRequest> captured,
            RuntimeException failure
    ) {
        return (PolicyV2Store) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{PolicyV2Store.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("publishProjection")) {
                        throw new AssertionError("materializer accessed private Policy v2 state");
                    }
                    PolicyV2Store.PublishProjectionRequest request =
                            (PolicyV2Store.PublishProjectionRequest) arguments[0];
                    captured.set(request);
                    if (failure != null) {
                        throw failure;
                    }
                    return request.projection();
                }
        );
    }

    private static PolicyV2PublicProjector.Source source(long revision) {
        PolicyV2Store.SanctionRevisionRecord sanctions = new PolicyV2Store.SanctionRevisionRecord(
                CASE_ID,
                0,
                PolicyV2Store.SanctionChangeKind.INITIAL,
                List.of(new SanctionSpec(
                        SanctionType.MUTE,
                        SanctionLength.temporary(Duration.ofHours(1))
                )),
                "private initial reason",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Optional.empty(),
                NOW
        );
        return new PolicyV2PublicProjector.Source(
                CASE_ID,
                BehavioralHistoryEntry.FindingState.CONFIRMED,
                "chat.spam",
                Optional.of("chat.spam"),
                NOW,
                sanctions,
                List.of(),
                List.of(),
                List.of(sanctions),
                List.of(),
                new PolicyV2PublicProjector.PublicMetadata(
                        Optional.of("PlayerOne"),
                        Optional.empty(),
                        "Chat & Spam",
                        "Repeated disruptive chat",
                        Optional.empty(),
                        true
                ),
                Map.of("chat.spam", "Chat spam"),
                "policy-v2-test",
                NOW.plusSeconds(30),
                revision
        );
    }
}
