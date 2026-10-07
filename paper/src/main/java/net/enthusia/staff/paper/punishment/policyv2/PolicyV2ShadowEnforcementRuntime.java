package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessCoordinator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2CapabilityGate;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.policyv2.publicview.PolicyV2PublicLifecyclePublisher;

/**
 * Non-authoritative runtime bridge for W3B enforcement state.
 *
 * <p>Every operation is fenced by the W5B shadow gate. This type may observe
 * blockers and persist automatic correction/satisfaction, but it exposes no
 * method that can apply a punitive remedy or deny a live capability.</p>
 */
final class PolicyV2ShadowEnforcementRuntime {
    private static final UUID SYSTEM_ACTOR = UUID.nameUUIDFromBytes(
            "enthusiastaff-policy-v2-shadow-runtime".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    private final BooleanSupplier enabled;
    private final Supplier<PolicyV2Store> canonicalStores;
    private final Supplier<PolicyV2EnforcementStore> enforcementStores;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    PolicyV2ShadowEnforcementRuntime(
            BooleanSupplier enabled,
            Supplier<PolicyV2Store> canonicalStores,
            Supplier<PolicyV2EnforcementStore> enforcementStores,
            AuthorizationPolicy authorization,
            Clock clock
    ) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.canonicalStores = Objects.requireNonNull(canonicalStores, "canonicalStores");
        this.enforcementStores = Objects.requireNonNull(enforcementStores, "enforcementStores");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Optional<PolicyV2AccessEvaluator.Decision> observeAccess(
            PolicyV2AccessEvaluator.Observation observation
    ) {
        if (!enabled.getAsBoolean()) {
            return Optional.empty();
        }
        Stores stores = stores();
        if (stores == null) {
            return Optional.empty();
        }
        PolicyV2RemedyService remedies = new PolicyV2RemedyService(
                stores.canonical(), stores.enforcement(), authorization, SYSTEM_ACTOR
        );
        PolicyV2AccessEvaluator evaluator = new PolicyV2AccessEvaluator(stores.enforcement());
        PolicyV2AccessEvaluator.Decision beforeRepair = evaluator.evaluate(observation);
        PolicyV2AccessCoordinator coordinator = new PolicyV2AccessCoordinator(evaluator, remedies);
        Instant now = clock.instant();
        PolicyV2AccessEvaluator.Decision decision = coordinator.evaluateAndRepair(observation, now);
        republishCorrections(stores.canonical(), observation, beforeRepair, now);
        return Optional.of(decision);
    }

    Optional<CapabilityObservation> observeCapability(UUID subjectId, Scope scope) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(scope, "scope");
        if (!enabled.getAsBoolean()) {
            return Optional.empty();
        }
        PolicyV2EnforcementStore store = enforcementStores.get();
        if (store == null) {
            return Optional.empty();
        }
        boolean wouldPermit = new PolicyV2CapabilityGate(store).permits(subjectId, scope);
        return Optional.of(new CapabilityObservation(scope, wouldPermit));
    }

    List<PolicyV2RemedyEnforcement> activeRemedies(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        if (!enabled.getAsBoolean()) {
            return List.of();
        }
        PolicyV2EnforcementStore store = enforcementStores.get();
        return store == null ? List.of() : store.activeFor(subjectId);
    }

    private static void republishCorrections(
            PolicyV2Store canonical,
            PolicyV2AccessEvaluator.Observation observation,
            PolicyV2AccessEvaluator.Decision beforeRepair,
            Instant now
    ) {
        if (beforeRepair.corrections().isEmpty()) {
            return;
        }
        PolicyV2PublicLifecyclePublisher publisher = new PolicyV2PublicLifecyclePublisher(canonical);
        for (PolicyV2AccessEvaluator.Correction correction : beforeRepair.corrections()) {
            publisher.republishExisting(
                    correction.caseId(),
                    Optional.of(observation.username()),
                    "shadow-public-auto|" + correction.caseId() + '|' + correction.remedyId()
                            + '|' + correction.expectedRevision(),
                    now,
                    now
            );
        }
    }

    private Stores stores() {
        PolicyV2Store canonical = canonicalStores.get();
        PolicyV2EnforcementStore enforcement = enforcementStores.get();
        return canonical == null || enforcement == null ? null : new Stores(canonical, enforcement);
    }

    record CapabilityObservation(Scope scope, boolean wouldPermit) {
    }

    private record Stores(PolicyV2Store canonical, PolicyV2EnforcementStore enforcement) {
    }
}
