package net.enthusia.staff.paper.config.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import org.junit.jupiter.api.Test;

class PolicyV2SnapshotPublisherTest {
    private static final String POLICY_ONE = "policy.1";
    private static final String POLICY_TWO = "policy.2";

    @Test
    void modeCanMoveFromDisabledToShadowWithoutChangingAuthorityModel() {
        PolicyV2SnapshotPublisher publisher = new PolicyV2SnapshotPublisher();
        PolicySnapshot snapshot = snapshot(POLICY_ONE, "Example");

        publisher.publish(configuration(PolicyV2FeatureMode.DISABLED, snapshot));
        assertFalse(publisher.shadowEnabled());

        publisher.publish(configuration(PolicyV2FeatureMode.SHADOW, snapshot));
        assertTrue(publisher.shadowEnabled());
        assertEquals(POLICY_ONE, publisher.activeSnapshot().version());
    }

    @Test
    void duplicateVersionWithDifferentContentIsRejectedAndLastKnownGoodRemainsActive() {
        PolicyV2SnapshotPublisher publisher = new PolicyV2SnapshotPublisher();
        PolicySnapshot original = snapshot(POLICY_ONE, "Original");
        publisher.publish(configuration(PolicyV2FeatureMode.SHADOW, original));

        assertThrows(
                PolicyV2PublicationException.class,
                () -> publisher.publish(configuration(
                        PolicyV2FeatureMode.SHADOW,
                        snapshot(POLICY_ONE, "Changed")
                ))
        );

        assertEquals(original, publisher.activeSnapshot());
        assertEquals(1, publisher.view().retainedVersions());
    }

    @Test
    void oldVersionReplayRemainsDeterministicAfterNewPublication() {
        PolicyV2SnapshotPublisher publisher = new PolicyV2SnapshotPublisher();
        PolicySnapshot first = snapshot(POLICY_ONE, "First");
        PolicySnapshot second = snapshot(POLICY_TWO, "Second");
        publisher.publish(configuration(PolicyV2FeatureMode.SHADOW, first));
        publisher.publish(configuration(PolicyV2FeatureMode.SHADOW, second));

        assertEquals(first, publisher.snapshot(POLICY_ONE).orElseThrow());
        assertEquals(second, publisher.snapshot(POLICY_TWO).orElseThrow());
        assertEquals(second, publisher.activeSnapshot());
        assertEquals(2, publisher.view().retainedVersions());
    }

    @Test
    void concurrentReloadCollisionPublishesExactlyOneWholeCandidate() throws Exception {
        PolicyV2SnapshotPublisher publisher = new PolicyV2SnapshotPublisher();
        publisher.publish(configuration(
                PolicyV2FeatureMode.SHADOW,
                snapshot(POLICY_ONE, "Base")
        ));
        PolicyV2Configuration left = configuration(
                PolicyV2FeatureMode.SHADOW,
                snapshot(POLICY_TWO, "Left")
        );
        PolicyV2Configuration right = configuration(
                PolicyV2FeatureMode.SHADOW,
                snapshot(POLICY_TWO, "Right")
        );
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger published = new AtomicInteger();
        AtomicInteger collisions = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var leftFuture = executor.submit(() -> publishAfter(start, publisher, left, published, collisions));
            var rightFuture = executor.submit(() -> publishAfter(start, publisher, right, published, collisions));
            start.countDown();
            leftFuture.get();
            rightFuture.get();
        }

        assertEquals(1, published.get());
        assertEquals(1, collisions.get());
        assertEquals(POLICY_TWO, publisher.activeSnapshot().version());
        assertTrue(
                publisher.activeSnapshot().offenses().getFirst().displayName().equals("Left")
                        || publisher.activeSnapshot().offenses().getFirst().displayName().equals("Right")
        );
    }

    private static void publishAfter(
            CountDownLatch start,
            PolicyV2SnapshotPublisher publisher,
            PolicyV2Configuration candidate,
            AtomicInteger published,
            AtomicInteger collisions
    ) {
        try {
            start.await();
            publisher.publish(candidate);
            published.incrementAndGet();
        } catch (PolicyV2PublicationException exception) {
            collisions.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static PolicyV2Configuration configuration(
            PolicyV2FeatureMode mode,
            PolicySnapshot snapshot
    ) {
        return new PolicyV2Configuration(
                PolicyV2Configuration.CURRENT_SCHEMA_VERSION,
                mode,
                snapshot.version(),
                Map.of(snapshot.version(), snapshot)
        );
    }

    private static PolicySnapshot snapshot(String version, String displayName) {
        OffensePolicy offense = new OffensePolicy(
                "example.review",
                displayName,
                "chat-spam",
                List.of(),
                new HistoryPolicy(Map.of(), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule(
                        "review",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                        new PolicyAction.RequiresReview("owner-policy.unresolved"),
                        List.of()
                ))
        );
        return new PolicySnapshot(version, List.of(offense));
    }
}
