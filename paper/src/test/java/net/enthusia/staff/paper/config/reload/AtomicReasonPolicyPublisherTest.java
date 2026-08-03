package net.enthusia.staff.paper.config.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;
import org.junit.jupiter.api.Test;

class AtomicReasonPolicyPublisherTest {
    @Test
    void publishesAndRestoresAliasesAndRemovedReasonsAsOneSnapshot() {
        ReasonPolicy original = policy("chat.original");
        AtomicReasonPolicyRepository repository = new AtomicReasonPolicyRepository("v1", List.of(original));
        AtomicReasonPolicyPublisher publisher = new AtomicReasonPolicyPublisher(repository);
        ReasonPolicyPublisher.Snapshot previous = publisher.snapshot();
        ReasonPolicy canonical = policy("chat.current");
        RemovedReason removed = new RemovedReason("chat.retired", "chat", "Retired reason");
        ReasonPolicyConfigurationLoader.LoadedPolicies candidate =
                new ReasonPolicyConfigurationLoader.LoadedPolicies(
                        "v2",
                        List.of(canonical),
                        Map.of("chat.old", canonical.id()),
                        List.of(removed)
                );

        publisher.publish(candidate);

        assertEquals("v2", repository.activeVersion());
        assertEquals(canonical, repository.find("chat.old").orElseThrow());
        assertEquals(
                ReasonPolicyRepository.ReasonAvailability.REMOVED,
                repository.describe(removed.id()).orElseThrow().availability()
        );
        assertTrue(publisher.snapshot().matches(candidate));

        publisher.restore(previous);

        assertEquals("v1", repository.activeVersion());
        assertTrue(repository.find(original.id()).isPresent());
        assertTrue(repository.find("chat.old").isEmpty());
        assertFalse(repository.describe(removed.id()).isPresent());
        assertTrue(publisher.snapshot().sameAs(previous));
    }

    private static ReasonPolicy policy(String id) {
        return new ReasonPolicy(
                id,
                "chat",
                id,
                10,
                true,
                List.of(new PunishmentStep(0, "Warning", List.of(
                        new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())
                )))
        );
    }
}
