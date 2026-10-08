package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.policyv2.DecayPolicy;
import net.enthusia.staff.domain.policyv2.HistoryPolicy;
import net.enthusia.staff.domain.policyv2.HistoryWindow;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.ResolutionRule;
import net.enthusia.staff.domain.policyv2.RuleCondition;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PolicyV2RealWorldEvidenceGateTest {
    private static final Instant NOW = Instant.parse("2026-10-08T17:00:00Z");
    private static final UUID REVIEWER = UUID.fromString("00000000-0000-0000-0000-000000000211");
    private static final UUID SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000212");
    private static final String OFFENSE = "safety.blackmail-extortion";
    private static final String CONTEXT = "coercion-context";
    private static final String VERIFIED = "real-world-leverage-verified";
    private static final Actor ADMIN = new Actor(REVIEWER, "admin", StaffRank.ADMIN);

    @Test
    void staffSelectingYesIsNeverIndependentEvidence() {
        var workflow = workflow(candidatePolicy(), PolicyV2RealWorldEvidenceGate.unavailable(),
                new AtomicInteger());
        assertThrows(IllegalArgumentException.class, () ->
                workflow.review(ADMIN, draft("real-world", true)));
        assertThrows(IllegalArgumentException.class, () ->
                workflow.review(ADMIN, draft("real-world", false).clearAnswer(VERIFIED)));
    }

    @Test
    void trustedVerifierReceivesExactActorSubjectFindingTimeAndSnapshot() {
        AtomicReference<PolicyV2RealWorldEvidenceGate.VerificationRequest> seen = new AtomicReference<>();
        var workflow = workflow(candidatePolicy(), request -> {
            seen.set(request);
            return true;
        }, new AtomicInteger());
        var review = workflow.review(ADMIN, draft("real-world", true));
        assertInstanceOf(PolicyAction.ExactWithApproval.class, review.resolution().action());
        assertEquals(PolicyV2ManualReview.ApprovalRoute.DIRECT_CONFIRM, review.route());
        assertEquals(REVIEWER, seen.get().reviewerId());
        assertEquals(SUBJECT, seen.get().subjectId());
        assertEquals(NOW, seen.get().incidentAt());
        assertEquals("candidate.v3", seen.get().policyVersion());
        assertEquals(review.finding(), seen.get().finding());
    }

    @Test
    void gameOnlyOrUnverifiedNeverInvokeVerifierOrBecomeTerminalSanction() {
        AtomicInteger calls = new AtomicInteger();
        var workflow = workflow(candidatePolicy(), ignored -> {
            calls.incrementAndGet();
            return true;
        }, new AtomicInteger());
        for (var draft : List.of(draft("game-only", false), draft("game-only", true),
                draft("uncertain", true), draft("real-world", false))) {
            assertThrows(IllegalArgumentException.class, () -> workflow.review(ADMIN, draft));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void evidenceRevokedAfterReviewPreventsShadowRecording() {
        AtomicBoolean evidencePresent = new AtomicBoolean(true);
        AtomicInteger writes = new AtomicInteger();
        var workflow = workflow(candidatePolicy(), ignored -> evidencePresent.get(), writes);
        var reviewed = workflow.review(ADMIN, draft("real-world", true));
        evidencePresent.set(false);
        var result = workflow.submitShadow(ADMIN, reviewed, "same-operation");
        assertInstanceOf(PolicyV2ManualWorkflow.SubmissionResult.Rejected.class, result);
        assertEquals(0, writes.get());
    }

    @Test
    void verifierFailureOnResubmissionRejectsWithoutRecording() {
        AtomicBoolean down = new AtomicBoolean(false);
        AtomicInteger writes = new AtomicInteger();
        var workflow = workflow(candidatePolicy(), ignored -> {
            if (down.get()) {
                throw new IllegalStateException("Evidence store is unavailable");
            }
            return true;
        }, writes);
        var reviewed = workflow.review(ADMIN, draft("real-world", true));
        down.set(true);
        var rejected = assertInstanceOf(PolicyV2ManualWorkflow.SubmissionResult.Rejected.class,
                workflow.submitShadow(ADMIN, reviewed, "provider-down"));
        assertTrue(rejected.message().contains("evidence"));
        assertEquals(0, writes.get());
    }

    @Test
    void ungatedTerminalRuleWithQuestionsCannotBanGameOnlyBlackmail() {
        var approvedQuestions = candidatePolicy().offenses().getFirst().attributes();
        var unsafe = new PolicySnapshot("unsafe.owner", List.of(new OffensePolicy(
                OFFENSE, "Blackmail", "safety-threats-privacy", approvedQuestions,
                new HistoryPolicy(Map.of(OFFENSE, 1.0), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule("ungated", new RuleCondition(
                        Map.of(), HistoryWindow.atLeast(0)),
                        permanentBan(), List.of()))
        )));
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger verifications = new AtomicInteger();
        var workflow = workflow(unsafe, ignored -> {
            verifications.incrementAndGet();
            return true;
        }, writes);
        assertThrows(IllegalArgumentException.class, () ->
                workflow.review(ADMIN, draft("game-only", false)));
        assertThrows(IllegalArgumentException.class, () ->
                workflow.review(ADMIN, draft("uncertain", true)));
        assertThrows(IllegalArgumentException.class, () ->
                workflow.review(ADMIN, draft("real-world", false)));
        assertEquals(0, verifications.get());
        assertEquals(0, writes.get());
    }

    @Test
    void historicalUngatedBlackmailSnapshotCannotBeReviewed() {
        var legacy = new PolicySnapshot("old.owner", List.of(new OffensePolicy(
                OFFENSE, "Blackmail", "safety-threats-privacy", List.of(),
                new HistoryPolicy(Map.of(OFFENSE, 1.0), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule("legacy", new RuleCondition(
                        Map.of(), HistoryWindow.atLeast(0)),
                        permanentBan(), List.of()))
        )));
        var workflow = workflow(legacy, ignored -> true, new AtomicInteger());
        var draft = PolicyV2ManualDraft.start(SUBJECT, NOW)
                .selectCategory(PolicyV2Category.SAFETY_THREATS_PRIVACY).selectOffense(OFFENSE);
        assertThrows(IllegalArgumentException.class, () -> workflow.review(ADMIN, draft));
    }

    private static PolicyV2ManualWorkflow workflow(
            PolicySnapshot snapshot, PolicyV2RealWorldEvidenceGate gate, AtomicInteger writes
    ) {
        return new PolicyV2ManualWorkflow(() -> snapshot, (subject, asOf) -> List.of(),
                (review, key, at) -> {
                    writes.incrementAndGet();
                    return UUID.fromString("00000000-0000-0000-0000-000000000213");
                },
                new DefaultAuthorizationPolicy(), Clock.fixed(NOW, ZoneOffset.UTC), gate);
    }

    private static PolicyV2ManualDraft draft(String context, boolean verified) {
        return PolicyV2ManualDraft.start(SUBJECT, NOW)
                .selectCategory(PolicyV2Category.SAFETY_THREATS_PRIVACY)
                .selectOffense(OFFENSE)
                .answer(CONTEXT, new IncidentAttributeValue.EnumValue(context))
                .answer(VERIFIED, new IncidentAttributeValue.BooleanValue(verified));
    }

    private static PolicySnapshot candidatePolicy() {
        var offense = new OffensePolicy(
                OFFENSE, "Real-world blackmail", "safety-threats-privacy",
                List.of(
                        IncidentAttributeDefinition.enumValue(CONTEXT, true,
                                Set.of("game-only", "real-world", "uncertain")),
                        IncidentAttributeDefinition.booleanValue(VERIFIED, true)
                ),
                new HistoryPolicy(Map.of(OFFENSE, 1.0), DecayPolicy.nonDecaying()),
                List.of(new ResolutionRule("terminal", new RuleCondition(
                        Map.of(
                                CONTEXT, Set.of(new IncidentAttributeValue.EnumValue("real-world")),
                                VERIFIED, Set.of(new IncidentAttributeValue.BooleanValue(true))
                        ),
                        HistoryWindow.atLeast(0)), permanentBan(), List.of()))
        );
        return new PolicySnapshot("candidate.v3", List.of(offense));
    }

    private static PolicyAction.ExactWithApproval permanentBan() {
        return new PolicyAction.ExactWithApproval(
                List.of(new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent())),
                StaffRank.ADMIN);
    }
}
