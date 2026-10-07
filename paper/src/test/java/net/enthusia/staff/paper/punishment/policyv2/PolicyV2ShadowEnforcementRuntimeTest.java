package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.HistoryAssessment;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2AccessEvaluator;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2EnforcementStore;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Condition;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2ShadowEnforcementRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID SUBJECT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BLOCKED_NAME = "BlockedName";

    @Test
    void disabledModeDoesNotReadAnyPolicyV2RuntimeStore() {
        AtomicInteger reads = new AtomicInteger();
        Supplier<PolicyV2Store> canonical = () -> {
            reads.incrementAndGet();
            return unusedCanonical();
        };
        Supplier<PolicyV2EnforcementStore> enforcement = () -> {
            reads.incrementAndGet();
            return new MemoryStore(List.of());
        };
        PolicyV2ShadowEnforcementRuntime runtime = runtime(() -> false, canonical, enforcement);

        assertTrue(runtime.observeAccess(observation("Player")).isEmpty());
        assertTrue(runtime.observeCapability(SUBJECT, Scope.REPORT_SUBMISSION).isEmpty());
        assertTrue(runtime.activeRemedies(SUBJECT).isEmpty());
        assertEquals(0, reads.get());
    }

    @Test
    void shadowAccessObservationReportsBlockerButCannotDenyLiveJoin() {
        MemoryStore store = new MemoryStore(List.of(record(
                "CASE000000000001",
                Scope.NETWORK_ACCESS,
                Condition.username(BLOCKED_NAME)
        )));
        PolicyV2ShadowEnforcementRuntime runtime =
                runtime(() -> true, PolicyV2ShadowEnforcementRuntimeTest::unusedCanonical, () -> store);

        var decision = runtime.observeAccess(observation(BLOCKED_NAME)).orElseThrow();

        assertFalse(decision.allowed());
        assertEquals(1, decision.blockers().size());
        assertEquals(0, store.writes);
    }

    @Test
    void shadowCapabilityObservationCoversAllCapabilityScopesWithoutMutation() {
        MemoryStore store = new MemoryStore(List.of(
                record("CASE000000000002", Scope.REPORT_SUBMISSION, Condition.manual()),
                record("CASE000000000003", Scope.MARKET_ACCESS, Condition.manual()),
                record("CASE000000000004", Scope.REPUTATION_ACCESS, Condition.manual())
        ));
        PolicyV2ShadowEnforcementRuntime runtime =
                runtime(() -> true, PolicyV2ShadowEnforcementRuntimeTest::unusedCanonical, () -> store);

        assertFalse(runtime.observeCapability(SUBJECT, Scope.REPORT_SUBMISSION).orElseThrow().wouldPermit());
        assertFalse(runtime.observeCapability(SUBJECT, Scope.MARKET_ACCESS).orElseThrow().wouldPermit());
        assertFalse(runtime.observeCapability(SUBJECT, Scope.REPUTATION_ACCESS).orElseThrow().wouldPermit());
        assertTrue(runtime.observeCapability(SUBJECT, Scope.CONTENT).orElseThrow().wouldPermit());
        assertTrue(runtime.observeCapability(SUBJECT, Scope.ASSET).orElseThrow().wouldPermit());
        assertEquals(0, store.writes);
    }

    @Test
    void disablingAfterShadowImmediatelyFencesFurtherObservations() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        MemoryStore store = new MemoryStore(List.of(
                record("CASE000000000005", Scope.REPORT_SUBMISSION, Condition.manual())
        ));
        PolicyV2ShadowEnforcementRuntime runtime =
                runtime(enabled::get, PolicyV2ShadowEnforcementRuntimeTest::unusedCanonical, () -> store);

        assertTrue(runtime.observeCapability(SUBJECT, Scope.REPORT_SUBMISSION).isPresent());
        int readsBeforeDisable = store.reads;
        enabled.set(false);

        assertTrue(runtime.observeCapability(SUBJECT, Scope.REPORT_SUBMISSION).isEmpty());
        assertTrue(runtime.activeRemedies(SUBJECT).isEmpty());
        assertEquals(readsBeforeDisable, store.reads);
    }

    @Test
    void correctedUsernameCompletesLifecyclePublishesSafeRevisionAndSurvivesRestart() {
        String caseId = "CASE000000000006";
        CanonicalState canonical = new CanonicalState(caseId);
        MemoryStore enforcement = new MemoryStore(List.of(correctiveRecord(caseId)));
        PolicyV2ShadowEnforcementRuntime first =
                runtime(() -> true, canonical::store, () -> enforcement);

        var repaired = first.observeAccess(observation("GoodName")).orElseThrow();

        assertTrue(repaired.allowed());
        assertEquals(1, enforcement.writes);
        assertEquals(PolicyV2Store.RemedyStatus.SATISFIED, canonical.remedy.status());
        assertEquals(1L, canonical.projection.revision());
        assertEquals(Optional.of("GoodName"), canonical.projection.currentPlayerName());

        PolicyV2ShadowEnforcementRuntime restarted =
                runtime(() -> true, canonical::store, () -> enforcement);
        var afterRestart = restarted.observeAccess(observation("GoodName")).orElseThrow();

        assertTrue(afterRestart.allowed());
        assertEquals(1, enforcement.writes);
        assertEquals(1L, canonical.projection.revision());
    }

    private static PolicyV2ShadowEnforcementRuntime runtime(
            java.util.function.BooleanSupplier enabled,
            Supplier<PolicyV2Store> canonical,
            Supplier<PolicyV2EnforcementStore> enforcement
    ) {
        return new PolicyV2ShadowEnforcementRuntime(
                enabled,
                canonical,
                enforcement,
                (actor, action) -> false,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static PolicyV2AccessEvaluator.Observation observation(String username) {
        return new PolicyV2AccessEvaluator.Observation(
                SUBJECT,
                username,
                PolicyV2AccessEvaluator.VpnState.UNKNOWN,
                Map.of()
        );
    }

    private static PolicyV2RemedyEnforcement record(
            String caseId,
            Scope scope,
            Condition condition
    ) {
        return new PolicyV2RemedyEnforcement(
                caseId,
                "remedy-" + scope.name().toLowerCase(java.util.Locale.ROOT),
                SUBJECT,
                RemedySpec.Type.ACCESS_RESTRICTION,
                scope,
                condition,
                Lifecycle.ENFORCED,
                1L,
                NOW
        );
    }

    private static PolicyV2RemedyEnforcement correctiveRecord(String caseId) {
        return new PolicyV2RemedyEnforcement(
                caseId,
                "profile-remedy",
                SUBJECT,
                RemedySpec.Type.CORRECT_PROFILE,
                Scope.NETWORK_ACCESS,
                Condition.username(BLOCKED_NAME),
                Lifecycle.ENFORCED,
                1L,
                NOW
        );
    }

    private static PolicyV2Store unusedCanonical() {
        return (PolicyV2Store) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{PolicyV2Store.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("canonical store must not be used by this observation");
                }
        );
    }

    private static final class CanonicalState {
        private final String caseId;
        private PolicyV2Store.RemedyRecord remedy;
        private PolicyV2PublicProjection projection;
        private final PolicyV2Store store;

        private CanonicalState(String caseId) {
            this.caseId = caseId;
            this.remedy = remedy(caseId, PolicyV2Store.RemedyStatus.REQUIRED, 1L);
            this.projection = projection(caseId, 0L, BLOCKED_NAME);
            this.store = proxy();
        }

        private PolicyV2Store store() {
            return store;
        }

        private PolicyV2Store proxy() {
            return (PolicyV2Store) Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{PolicyV2Store.class},
                    (proxy, method, arguments) -> invoke(method.getName(), arguments)
            );
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "findCase" -> Optional.of(caseRecord());
                case "updateRemedy" -> update((PolicyV2Store.RemedyUpdateRequest) arguments[0]);
                case "publicProjection" -> Optional.of(projection);
                case "publishProjection" -> publish((PolicyV2Store.PublishProjectionRequest) arguments[0]);
                case "findingRevisions", "appealHistory" -> List.of();
                case "sanctionRevisions" -> List.of(caseRecord().currentSanctions());
                default -> throw new AssertionError("unexpected canonical method " + method);
            };
        }

        private PolicyV2Store.RemedyRecord update(PolicyV2Store.RemedyUpdateRequest request) {
            if (request.expectedRevision() != remedy.revision()) {
                throw new PolicyV2Store.Conflict("stale remedy");
            }
            remedy = remedy(caseId, request.status(), remedy.revision() + 1L);
            return remedy;
        }

        private PolicyV2PublicProjection publish(PolicyV2Store.PublishProjectionRequest request) {
            projection = request.projection();
            return projection;
        }

        private PolicyV2Store.CaseRecord caseRecord() {
            IncidentFinding finding = new IncidentFinding("profile.bad", Map.of());
            PolicyResolution resolution = PolicyResolution.requiresReview(
                    "policy-v2-test",
                    finding.offenseId(),
                    "review-only",
                    HistoryAssessment.empty()
            );
            PolicyV2Store.SanctionRevisionRecord sanctions = new PolicyV2Store.SanctionRevisionRecord(
                    caseId,
                    0L,
                    PolicyV2Store.SanctionChangeKind.INITIAL,
                    List.of(new SanctionSpec(SanctionType.MUTE, SanctionLength.permanent())),
                    "private",
                    SUBJECT,
                    Optional.empty(),
                    NOW
            );
            return new PolicyV2Store.CaseRecord(
                    caseId,
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    finding,
                    Optional.of(finding),
                    BehavioralHistoryEntry.FindingState.CONFIRMED,
                    NOW,
                    0L,
                    0L,
                    resolution,
                    List.of(),
                    List.of(remedy),
                    sanctions
            );
        }

        private static PolicyV2Store.RemedyRecord remedy(
                String caseId,
                PolicyV2Store.RemedyStatus status,
                long revision
        ) {
            return new PolicyV2Store.RemedyRecord(
                    caseId,
                    new RemedySpec("profile-remedy", RemedySpec.Type.CORRECT_PROFILE, "private"),
                    status,
                    revision,
                    NOW
            );
        }

        private static PolicyV2PublicProjection projection(String caseId, long revision, String player) {
            return new PolicyV2PublicProjection(
                    caseId,
                    Optional.of(player),
                    Optional.of(BLOCKED_NAME),
                    "Profile",
                    "Username compliance",
                    "Profile compliance required",
                    Optional.empty(),
                    PolicyV2PublicProjection.Status.ACTIVE,
                    PolicyV2PublicProjection.AppealStatus.NONE,
                    NOW,
                    Optional.empty(),
                    List.of(new PolicyV2PublicProjection.PublicSanction(
                            PolicyV2PublicProjection.PublicSanctionType.MUTE,
                            "Mute",
                            PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                            Optional.empty()
                    )),
                    List.of(new PolicyV2PublicProjection.PublicRemedy(
                            PolicyV2PublicProjection.PublicRemedyType.PROFILE_COMPLIANCE,
                            "Profile compliance",
                            PolicyV2PublicProjection.PublicRemedy.RemedyStatus.REQUIRED
                    )),
                    List.of(new PolicyV2PublicProjection.PublicRevision(
                            PolicyV2PublicProjection.RevisionType.ISSUED,
                            NOW,
                            "Case issued"
                    )),
                    Optional.of("policy-v2-test"),
                    revision
            );
        }
    }

    private static final class MemoryStore implements PolicyV2EnforcementStore {
        private final List<PolicyV2RemedyEnforcement> records = new ArrayList<>();
        private int reads;
        private int writes;

        private MemoryStore(List<PolicyV2RemedyEnforcement> records) {
            this.records.addAll(records);
        }

        @Override
        public PolicyV2RemedyEnforcement register(RegisterRequest request) {
            writes++;
            throw new AssertionError("shadow observation must not register remedies");
        }

        @Override
        public Optional<PolicyV2RemedyEnforcement> find(String caseId, String remedyId) {
            reads++;
            return records.stream()
                    .filter(record -> record.caseId().equals(caseId) && record.remedyId().equals(remedyId))
                    .findFirst();
        }

        @Override
        public List<PolicyV2RemedyEnforcement> activeFor(UUID subjectId) {
            reads++;
            return records.stream()
                    .filter(record -> record.subjectId().equals(subjectId) && record.active())
                    .toList();
        }

        @Override
        public PolicyV2RemedyEnforcement transition(TransitionRequest request) {
            writes++;
            PolicyV2RemedyEnforcement current = findRecord(request.caseId(), request.remedyId());
            if (current.revision() != request.expectedRevision()) {
                throw new PolicyV2Store.Conflict("stale enforcement");
            }
            PolicyV2RemedyEnforcement updated = new PolicyV2RemedyEnforcement(
                    current.caseId(),
                    current.remedyId(),
                    current.subjectId(),
                    current.remedyType(),
                    current.scope(),
                    current.condition(),
                    request.lifecycle(),
                    current.revision() + 1L,
                    request.occurredAt()
            );
            records.set(records.indexOf(current), updated);
            return updated;
        }

        private PolicyV2RemedyEnforcement findRecord(String caseId, String remedyId) {
            return records.stream()
                    .filter(record -> record.caseId().equals(caseId) && record.remedyId().equals(remedyId))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
