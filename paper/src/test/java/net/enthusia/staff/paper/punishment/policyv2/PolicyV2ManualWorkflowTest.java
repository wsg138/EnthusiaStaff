package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.config.policyv2.PolicyV2Configuration;
import net.enthusia.staff.paper.config.policyv2.PolicyV2FeatureMode;
import net.enthusia.staff.paper.config.policyv2.PolicyV2SnapshotPublisher;
import org.junit.jupiter.api.Test;

class PolicyV2ManualWorkflowTest {
    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");
    private static final String POLICY_ONE = "policy-1";
    private static final String HARASSMENT_ID = "harassment.targeted";
    private static final String TARGETED_ATTRIBUTE = "targeted";
    private static final UUID TARGET = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void routesOnlyQuestionsDeclaredByTheSelectedOffense() {
        PolicySnapshot snapshot = standardSnapshot(POLICY_ONE);
        PolicyV2ManualWorkflow workflow = workflow(snapshot, List.of());

        PolicyV2ManualDraft draft = PolicyV2ManualDraft.start(TARGET, NOW)
                .selectCategory(PolicyV2Category.HARASSMENT_ABUSE)
                .selectOffense(HARASSMENT_ID);

        assertEquals(List.of(TARGETED_ATTRIBUTE), workflow.questions(draft).stream()
                .map(IncidentAttributeDefinition::id).toList());
        PolicyV2ManualDraft answered = workflow.answer(
                draft, TARGETED_ATTRIBUTE, new IncidentAttributeValue.BooleanValue(true)
        );
        assertTrue(answered.attributes().containsKey(TARGETED_ATTRIBUTE));
        assertThrows(IllegalArgumentException.class, () -> workflow.answer(
                draft, "severity", new IncidentAttributeValue.EnumValue("high")
        ));
    }

    @Test
    void backAndEditClearDependentAnswersInsteadOfReusingStaleFacts() {
        PolicyV2ManualDraft draft = PolicyV2ManualDraft.start(TARGET, NOW)
                .selectCategory(PolicyV2Category.HARASSMENT_ABUSE)
                .selectOffense(HARASSMENT_ID)
                .answer(TARGETED_ATTRIBUTE, new IncidentAttributeValue.BooleanValue(true));

        PolicyV2ManualDraft offenseEdit = draft.editOffense();
        assertTrue(offenseEdit.offenseId().isEmpty());
        assertTrue(offenseEdit.attributes().isEmpty());
        assertEquals(PolicyV2Category.HARASSMENT_ABUSE.id(), offenseEdit.categoryId().orElseThrow());

        PolicyV2ManualDraft categoryEdit = draft.editCategory();
        assertTrue(categoryEdit.categoryId().isEmpty());
        assertTrue(categoryEdit.offenseId().isEmpty());
        assertTrue(categoryEdit.attributes().isEmpty());
    }

    @Test
    void exactPolicyRoutesDirectOrApprovalAccordingToCurrentAuthority() {
        PolicyV2ManualWorkflow workflow = workflow(standardSnapshot(POLICY_ONE), List.of());
        PolicyV2ManualDraft draft = answeredHarassment();

        assertEquals(
                PolicyV2ManualReview.ApprovalRoute.DIRECT_CONFIRM,
                workflow.review(actor(StaffRank.MOD), draft).route()
        );
        assertEquals(
                PolicyV2ManualReview.ApprovalRoute.APPROVAL_REQUIRED,
                workflow.review(actor(StaffRank.DEVELOPER), draft).route()
        );
    }

    @Test
    void boundedPolicyRequiresConfiguredRankBeforeDirectConfirmation() {
        PolicyV2ManualWorkflow workflow = workflow(boundedSnapshot(POLICY_ONE), List.of());
        PolicyV2ManualDraft draft = answeredHarassment();

        assertEquals(
                PolicyV2ManualReview.ApprovalRoute.APPROVAL_REQUIRED,
                workflow.review(actor(StaffRank.MOD), draft).route()
        );
        assertEquals(
                PolicyV2ManualReview.ApprovalRoute.DIRECT_CONFIRM,
                workflow.review(actor(StaffRank.ADMIN), draft).route()
        );
    }

    @Test
    void policyGapAlwaysRoutesToAdminFounderReviewAndNeverInventsSanction() {
        PolicyV2ManualWorkflow workflow = workflow(standardSnapshot(POLICY_ONE), List.of());
        PolicyV2ManualDraft gap = PolicyV2ManualDraft.start(TARGET, NOW)
                .selectCategory(PolicyV2Category.POLICY_GAP)
                .describePolicyGap("Previously unseen exploit behavior affecting player state.");

        PolicyV2ManualReview review = workflow.review(actor(StaffRank.FOUNDER), gap);

        assertEquals(PolicyV2ManualReview.ApprovalRoute.ADMIN_FOUNDER_REVIEW, review.route());
        assertTrue(review.resolution().requiresReview());
        assertInstanceOf(PolicyAction.RequiresReview.class, review.resolution().action());
    }

    @Test
    void staleReviewRecalculatesInsteadOfRecordingOldPolicy() {
        AtomicReference<PolicySnapshot> snapshot = new AtomicReference<>(standardSnapshot(POLICY_ONE));
        RecordingShadow shadow = new RecordingShadow();
        PolicyV2ManualWorkflow workflow = workflow(snapshot::get, List.of(), shadow, new DefaultAuthorizationPolicy());
        PolicyV2ManualReview first = workflow.review(actor(StaffRank.MOD), answeredHarassment());

        snapshot.set(standardSnapshot("policy-2"));

        PolicyV2ManualWorkflow.SubmissionResult result =
                workflow.submitShadow(actor(StaffRank.MOD), first, "stale-review");

        PolicyV2ManualWorkflow.SubmissionResult.Stale stale =
                assertInstanceOf(PolicyV2ManualWorkflow.SubmissionResult.Stale.class, result);
        assertEquals("policy-2", stale.refreshed().snapshot().version());
        assertEquals(0, shadow.writes);
    }

    @Test
    void staleGuiReviewAfterAtomicReloadCannotRecordPreviousSnapshot() {
        PolicyV2SnapshotPublisher publisher = new PolicyV2SnapshotPublisher();
        publisher.publish(new PolicyV2Configuration(
                PolicyV2Configuration.CURRENT_SCHEMA_VERSION,
                PolicyV2FeatureMode.SHADOW,
                POLICY_ONE,
                Map.of(POLICY_ONE, standardSnapshot(POLICY_ONE))
        ));
        RecordingShadow shadow = new RecordingShadow();
        PolicyV2ManualWorkflow workflow = workflow(
                publisher::activeSnapshot,
                List.of(),
                shadow,
                new DefaultAuthorizationPolicy()
        );
        PolicyV2ManualReview reviewed = workflow.review(actor(StaffRank.MOD), answeredHarassment());

        publisher.publish(new PolicyV2Configuration(
                PolicyV2Configuration.CURRENT_SCHEMA_VERSION,
                PolicyV2FeatureMode.SHADOW,
                "policy-2",
                Map.of("policy-2", standardSnapshot("policy-2"))
        ));

        PolicyV2ManualWorkflow.SubmissionResult.Stale stale = assertInstanceOf(
                PolicyV2ManualWorkflow.SubmissionResult.Stale.class,
                workflow.submitShadow(actor(StaffRank.MOD), reviewed, "reload-stale")
        );
        assertEquals("policy-2", stale.refreshed().snapshot().version());
        assertEquals(0, shadow.writes);
    }

    @Test
    void successfulRetryUsesRecorderIdempotencyAndConflictIsExplicit() {
        RecordingShadow shadow = new RecordingShadow();
        PolicyV2ManualWorkflow workflow = workflow(
                () -> standardSnapshot(POLICY_ONE), List.of(), shadow, new DefaultAuthorizationPolicy()
        );
        PolicyV2ManualReview review = workflow.review(actor(StaffRank.MOD), answeredHarassment());

        var first = assertInstanceOf(
                PolicyV2ManualWorkflow.SubmissionResult.Recorded.class,
                workflow.submitShadow(actor(StaffRank.MOD), review, "same-operation")
        );
        var retry = assertInstanceOf(
                PolicyV2ManualWorkflow.SubmissionResult.Recorded.class,
                workflow.submitShadow(actor(StaffRank.MOD), review, "same-operation")
        );

        assertEquals(first.evaluationId(), retry.evaluationId());
        assertEquals(2, shadow.calls);
        assertEquals(1, shadow.writes);

        shadow.conflict = true;
        assertInstanceOf(
                PolicyV2ManualWorkflow.SubmissionResult.Conflict.class,
                workflow.submitShadow(actor(StaffRank.MOD), review, "collision")
        );
    }

    @Test
    void authorityIsRecheckedAtSubmission() {
        AtomicBoolean active = new AtomicBoolean(true);
        AuthorizationPolicy changing = (actor, action) -> active.get();
        PolicyV2ManualWorkflow workflow = workflow(
                () -> standardSnapshot(POLICY_ONE), List.of(), new RecordingShadow(), changing
        );
        Actor mod = actor(StaffRank.MOD);
        PolicyV2ManualReview review = workflow.review(mod, answeredHarassment());

        active.set(false);

        assertInstanceOf(
                PolicyV2ManualWorkflow.SubmissionResult.Rejected.class,
                workflow.submitShadow(mod, review, "authority-expired")
        );
    }

    @Test
    void relevantHistoryIsResolvedFromW2BoundaryAtIncidentTime() {
        BehavioralHistoryEntry prior = new BehavioralHistoryEntry(
                "CASE-OLD", NOW.minus(Duration.ofDays(3)),
                HARASSMENT_ID, HARASSMENT_ID,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        );
        PolicyV2ManualWorkflow workflow = workflow(standardSnapshot(POLICY_ONE), List.of(prior));

        PolicyV2ManualReview review = workflow.review(actor(StaffRank.MOD), answeredHarassment());

        assertEquals(List.of(prior), review.historyInputs());
        assertEquals(1, review.resolution().history().contributions().size());
    }

    private static PolicyV2ManualDraft answeredHarassment() {
        return PolicyV2ManualDraft.start(TARGET, NOW)
                .selectCategory(PolicyV2Category.HARASSMENT_ABUSE)
                .selectOffense(HARASSMENT_ID)
                .answer(TARGETED_ATTRIBUTE, new IncidentAttributeValue.BooleanValue(true));
    }

    private static PolicyV2ManualWorkflow workflow(PolicySnapshot snapshot, List<BehavioralHistoryEntry> history) {
        return workflow(() -> snapshot, history, new RecordingShadow(), new DefaultAuthorizationPolicy());
    }

    private static PolicyV2ManualWorkflow workflow(
            java.util.function.Supplier<PolicySnapshot> snapshots,
            List<BehavioralHistoryEntry> history,
            PolicyV2ManualWorkflow.ShadowRecorder recorder,
            AuthorizationPolicy authorization
    ) {
        return new PolicyV2ManualWorkflow(
                snapshots,
                (subjectId, asOf) -> history,
                recorder,
                authorization,
                CLOCK
        );
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(ACTOR, rank.name(), rank);
    }

    private static PolicySnapshot standardSnapshot(String version) {
        OffensePolicy harassment = offense(
                HARASSMENT_ID,
                "Targeted Harassment",
                "harassment-abuse",
                List.of(IncidentAttributeDefinition.booleanValue(TARGETED_ATTRIBUTE, true)),
                new PolicyAction.Exact(List.of(warning()))
        );
        OffensePolicy cheating = offense(
                "cheating.unauthorized-client",
                "Unauthorized Client",
                "cheating",
                List.of(IncidentAttributeDefinition.enumValue("severity", true, Set.of("low", "high"))),
                new PolicyAction.Exact(List.of(mute()))
        );
        return new PolicySnapshot(version, List.of(harassment, cheating));
    }

    private static PolicySnapshot boundedSnapshot(String version) {
        PolicyAction action = new PolicyAction.Bounded(
                List.of(List.of(warning()), List.of(mute())),
                StaffRank.ADMIN
        );
        return new PolicySnapshot(version, List.of(offense(
                HARASSMENT_ID,
                "Targeted Harassment",
                "harassment-abuse",
                List.of(IncidentAttributeDefinition.booleanValue(TARGETED_ATTRIBUTE, true)),
                action
        )));
    }

    private static OffensePolicy offense(
            String id,
            String name,
            String category,
            List<IncidentAttributeDefinition> attributes,
            PolicyAction action
    ) {
        return new OffensePolicy(
                id,
                name,
                category,
                attributes,
                new HistoryPolicy(
                        Map.of(id, 1.0),
                        DecayPolicy.exponential(Duration.ofDays(30), 0.25, 2.0)
                ),
                List.of(new ResolutionRule(
                        "base",
                        new RuleCondition(Map.of(), HistoryWindow.atLeast(0.0)),
                        action,
                        List.of(new RemedySpec("remove-content", RemedySpec.Type.REMOVE_CONTENT, "Remove violating content"))
                ))
        );
    }

    private static SanctionSpec warning() {
        return new SanctionSpec(SanctionType.WARNING, SanctionLength.instant());
    }

    private static SanctionSpec mute() {
        return new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofHours(12)));
    }

    private static final class RecordingShadow implements PolicyV2ManualWorkflow.ShadowRecorder {
        private final Map<String, UUID> recorded = new java.util.concurrent.ConcurrentHashMap<>();
        private int calls;
        private int writes;
        private boolean conflict;

        @Override
        public UUID record(PolicyV2ManualReview review, String operationKey, Instant evaluatedAt) {
            calls++;
            if (conflict) {
                throw new PolicyV2Store.Conflict("collision");
            }
            UUID existing = recorded.get(operationKey);
            if (existing != null) {
                return existing;
            }
            UUID created = UUID.nameUUIDFromBytes(operationKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            recorded.put(operationKey, created);
            writes++;
            return created;
        }
    }
}
