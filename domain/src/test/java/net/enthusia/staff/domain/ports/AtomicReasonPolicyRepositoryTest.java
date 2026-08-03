package net.enthusia.staff.domain.ports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.escalation.RemovedReason;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class AtomicReasonPolicyRepositoryTest {
    private static final String INITIAL_VERSION = "v1";
    private static final String CHAT_POLICY_ID = "chat.harassment";
    private static final String POLAR_TEMPLATE_ID = "cheating.polar.template";
    private static final String POLAR_SPEED_ID = "cheating.polar.speed";

    @Test
    void createsIndependentPolarFamiliesFromTheValidatedTemplate() {
        ReasonPolicy template = policy(POLAR_TEMPLATE_ID);
        AtomicReasonPolicyRepository repository = new AtomicReasonPolicyRepository(
                INITIAL_VERSION,
                List.of(template)
        );

        ReasonPolicy speed = repository.find(POLAR_SPEED_ID).orElseThrow();
        ReasonPolicy reach = repository.find("cheating.polar.reach").orElseThrow();

        assertEquals(POLAR_SPEED_ID, speed.family());
        assertEquals("cheating.polar.reach", reach.family());
        assertNotEquals(speed.family(), reach.family());
        assertTrue(repository.find("cheating.polar.Invalid Family").isEmpty());
    }

    @Test
    void removedPolarIdentifiersBlockTemplateExpansion() {
        RemovedReason removed = new RemovedReason(
                POLAR_SPEED_ID,
                "cheating.polar",
                "Retired Polar speed detection"
        );
        AtomicReasonPolicyRepository repository = new AtomicReasonPolicyRepository(
                "v2",
                List.of(policy(POLAR_TEMPLATE_ID)),
                Map.of(),
                List.of(removed)
        );

        assertTrue(repository.find(removed.id()).isEmpty());
        assertTrue(repository.resolve(removed.id()).isEmpty());
        assertEquals(
                ReasonPolicyRepository.ReasonAvailability.REMOVED,
                repository.describe(removed.id()).orElseThrow().availability()
        );
    }

    @Test
    void aliasesResolveToCanonicalPoliciesWithoutBecomingSelectableEntries() {
        ReasonPolicy canonical = policy(CHAT_POLICY_ID);
        AtomicReasonPolicyRepository repository = new AtomicReasonPolicyRepository(
                "v2",
                List.of(canonical),
                Map.of("chat.targeted-harassment", canonical.id()),
                List.of()
        );

        ReasonPolicy resolved = repository.resolve("chat.targeted-harassment").orElseThrow().policy();
        ReasonPolicyRepository.ReasonDescriptor descriptor = repository
                .describe("chat.targeted-harassment")
                .orElseThrow();

        assertEquals(canonical, resolved);
        assertEquals("v2", repository.resolve("chat.targeted-harassment").orElseThrow().version());
        assertEquals(ReasonPolicyRepository.ReasonAvailability.ALIAS, descriptor.availability());
        assertEquals(canonical.id(), descriptor.canonicalId());
        assertTrue(descriptor.resolvesToActivePolicy());
        assertEquals(List.of(canonical.id()), repository.all().stream().map(ReasonPolicy::id).toList());
    }

    @Test
    void removedReasonsRemainReadableButCannotResolveOrBecomeSelectable() {
        RemovedReason removed = new RemovedReason("chat.legacy-abuse", "chat", "Legacy abusive language");
        AtomicReasonPolicyRepository repository = new AtomicReasonPolicyRepository(
                "v3",
                List.of(policy(CHAT_POLICY_ID)),
                Map.of(),
                List.of(removed)
        );

        ReasonPolicyRepository.ReasonDescriptor descriptor = repository.describe(removed.id()).orElseThrow();

        assertEquals(ReasonPolicyRepository.ReasonAvailability.REMOVED, descriptor.availability());
        assertEquals(removed.publicReason(), descriptor.publicReason());
        assertFalse(descriptor.resolvesToActivePolicy());
        assertTrue(repository.find(removed.id()).isEmpty());
        assertTrue(repository.resolve(removed.id()).isEmpty());
        assertFalse(repository.all().stream().anyMatch(policy -> policy.id().equals(removed.id())));
    }

    @Test
    void rejectsAliasesThatOverlapOrDoNotTargetAnActivePolicy() {
        ReasonPolicy canonical = policy(CHAT_POLICY_ID);
        RemovedReason removed = new RemovedReason("chat.retired", "chat", "Retired reason");

        assertThrows(IllegalArgumentException.class, () -> new AtomicReasonPolicyRepository(
                INITIAL_VERSION, List.of(canonical), Map.of(canonical.id(), canonical.id()), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new AtomicReasonPolicyRepository(
                INITIAL_VERSION, List.of(canonical), Map.of(removed.id(), canonical.id()), List.of(removed)
        ));
        assertThrows(IllegalArgumentException.class, () -> new AtomicReasonPolicyRepository(
                INITIAL_VERSION, List.of(canonical), Map.of("chat.old", "chat.missing"), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new AtomicReasonPolicyRepository(
                INITIAL_VERSION,
                List.of(canonical),
                Map.of("chat.old", "chat.older", "chat.older", canonical.id()),
                List.of()
        ));
    }

    private static ReasonPolicy policy(String id) {
        return new ReasonPolicy(
                id,
                id,
                "Test reason",
                75,
                false,
                List.of(new PunishmentStep(0, "30 day ban", List.of(
                        new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(30)))
                )))
        );
    }
}
