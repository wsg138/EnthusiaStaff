package net.enthusia.staff.integration;

import static net.enthusia.staff.integration.PolicyV2EnforcementTestSupport.actor;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnOrchestrator;
import net.enthusia.staff.domain.policyv2.appeal.PolicyV2FullOverturnStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyService;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.integration.PolicyV2PersistenceTestSupport.Fixture;
import net.enthusia.staff.persistence.JdbcPolicyV2EnforcementStore;
import net.enthusia.staff.persistence.JdbcPolicyV2FullOverturnStore;
import net.enthusia.staff.persistence.JdbcPolicyV2Store;

final class PolicyV2FullOverturnRuntimeTestSupport {
    private PolicyV2FullOverturnRuntimeTestSupport() {
    }

    static Runtime runtime(
            HikariDataSource dataSource,
            RecordingProviders providers,
            PolicyV2FullOverturnOrchestrator.FailureProbe failureProbe,
            UUID systemActorId
    ) {
        PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
        PolicyV2EnforcementStore enforcement = new JdbcPolicyV2EnforcementStore(dataSource);
        PolicyV2FullOverturnStore operations = new JdbcPolicyV2FullOverturnStore(dataSource);
        PolicyV2FullOverturnOrchestrator orchestrator = new PolicyV2FullOverturnOrchestrator(
                canonical,
                enforcement,
                operations,
                new DefaultAuthorizationPolicy(),
                providers::terminateSanctions,
                providers::cleanupRemedy,
                failureProbe
        );
        return new Runtime(canonical, enforcement, operations, orchestrator);
    }

    static PolicyV2FullOverturnOrchestrator.Command command(
            UUID operationId,
            Fixture fixture,
            Actor actor,
            String appealReference,
            Instant occurredAt
    ) {
        return new PolicyV2FullOverturnOrchestrator.Command(
                operationId,
                fixture.caseId(),
                appealReference,
                actor,
                "Appeal fully overturned the Policy v2 finding",
                occurredAt
        );
    }

    static Actor createEnforcedAssetRemedy(
            HikariDataSource dataSource,
            Fixture fixture,
            UUID systemActorId,
            Instant now
    ) {
        PolicyV2Store canonical = new JdbcPolicyV2Store(dataSource);
        PolicyV2EnforcementStore enforcement = new JdbcPolicyV2EnforcementStore(dataSource);
        PolicyV2RemedyService service = new PolicyV2RemedyService(
                canonical,
                enforcement,
                new DefaultAuthorizationPolicy(),
                systemActorId
        );
        RemedySpec remedy = assetRemedy();
        PolicyV2EnforcementTestSupport.createPolicyCase(
                canonical,
                fixture,
                "economy.confiscation",
                remedy,
                "asset-case:434",
                now.minusSeconds(3)
        );
        Actor admin = actor(fixture.actorId(), StaffRank.ADMIN);
        PolicyV2RemedyEnforcement required = service.register(
                admin,
                new PolicyV2RemedyService.RegisterCommand(
                        fixture.caseId(),
                        remedy.id(),
                        fixture.targetId(),
                        Scope.ASSET,
                        Condition.manual(),
                        "asset-register:434",
                        now.minusSeconds(2)
                )
        );
        service.enforce(
                admin,
                fixture.caseId(),
                remedy.id(),
                required.revision(),
                (ignored, providerOperationId) -> { },
                "asset-enforce:434",
                now.minusSeconds(1)
        );
        return admin;
    }

    private static RemedySpec assetRemedy() {
        return new RemedySpec(
                "confiscated-assets",
                RemedySpec.Type.CONFISCATE,
                "Restore confiscated assets after overturn"
        );
    }

    record Runtime(
            PolicyV2Store canonical,
            PolicyV2EnforcementStore enforcement,
            PolicyV2FullOverturnStore operations,
            PolicyV2FullOverturnOrchestrator orchestrator
    ) {
    }

    static final class RecordingProviders {
        final Set<UUID> sanctionEffects = new HashSet<>();
        final Set<UUID> remedyEffects = new HashSet<>();
        final AtomicInteger sanctionAttempts = new AtomicInteger();
        final AtomicInteger remedyAttempts = new AtomicInteger();

        void terminateSanctions(UUID operationId, String caseId, List<SanctionSpec> sanctions) {
            if (caseId == null || caseId.isBlank() || sanctions.isEmpty()) {
                throw new AssertionError("invalid sanction provider input");
            }
            sanctionAttempts.incrementAndGet();
            sanctionEffects.add(operationId);
        }

        void cleanupRemedy(UUID operationId, PolicyV2RemedyEnforcement enforcement) {
            if (enforcement.lifecycle() != Lifecycle.ENFORCED) {
                throw new AssertionError("cleanup requires enforced remedy");
            }
            remedyAttempts.incrementAndGet();
            remedyEffects.add(operationId);
        }
    }
}
