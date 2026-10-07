package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicyResolver;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

public final class PolicyV2ManualWorkflow {
    private static final int MAX_OPERATION_KEY = 128;
    private static final String POLICY_GAP_OFFENSE = "policy-gap.unclassified";

    @FunctionalInterface
    public interface HistorySource {
        List<BehavioralHistoryEntry> completeHistory(UUID subjectId, Instant asOf);
    }

    @FunctionalInterface
    public interface ShadowRecorder {
        UUID record(PolicyV2ManualReview review, String operationKey, Instant evaluatedAt);
    }

    public sealed interface SubmissionResult
            permits SubmissionResult.Recorded, SubmissionResult.Stale,
            SubmissionResult.Rejected, SubmissionResult.Conflict {
        record Recorded(UUID evaluationId, PolicyV2ManualReview.ApprovalRoute route)
                implements SubmissionResult {
        }

        record Stale(PolicyV2ManualReview refreshed) implements SubmissionResult {
        }

        record Rejected(String message) implements SubmissionResult {
        }

        record Conflict(String message) implements SubmissionResult {
        }
    }

    private final Supplier<PolicySnapshot> snapshots;
    private final HistorySource histories;
    private final ShadowRecorder recorder;
    private final AuthorizationPolicy authorization;
    private final PolicyResolver resolver;
    private final Clock clock;

    public PolicyV2ManualWorkflow(
            Supplier<PolicySnapshot> snapshots,
            HistorySource histories,
            ShadowRecorder recorder,
            AuthorizationPolicy authorization,
            Clock clock
    ) {
        this(snapshots, histories, recorder, authorization, new PolicyResolver(), clock);
    }

    PolicyV2ManualWorkflow(
            Supplier<PolicySnapshot> snapshots,
            HistorySource histories,
            ShadowRecorder recorder,
            AuthorizationPolicy authorization,
            PolicyResolver resolver,
            Clock clock
    ) {
        this.snapshots = java.util.Objects.requireNonNull(snapshots, "snapshots");
        this.histories = java.util.Objects.requireNonNull(histories, "histories");
        this.recorder = java.util.Objects.requireNonNull(recorder, "recorder");
        this.authorization = java.util.Objects.requireNonNull(authorization, "authorization");
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public boolean mayStart(Actor actor) {
        return actor != null && (authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || authorization.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION));
    }

    public List<PolicyV2Category> categories() {
        return PolicyV2Category.ordered();
    }

    public List<OffensePolicy> offenses(PolicyV2Category category) {
        if (category == null || category.reviewOnly()) {
            return List.of();
        }
        return currentSnapshot().offenses().stream()
                .filter(offense -> category.matchesNavigationId(offense.navigationGroupId()))
                .sorted(Comparator.comparing(OffensePolicy::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<IncidentAttributeDefinition> questions(PolicyV2ManualDraft draft) {
        if (draft == null || draft.isPolicyGap() || draft.offenseId().isEmpty()) {
            return List.of();
        }
        PolicyV2Category category = selectedCategory(draft);
        if (category == null) {
            return List.of();
        }
        return currentSnapshot().offense(draft.offenseId().orElseThrow())
                .filter(offense -> category.matchesNavigationId(offense.navigationGroupId()))
                .map(OffensePolicy::attributes)
                .orElse(List.of());
    }

    public PolicyV2ManualDraft answer(
            PolicyV2ManualDraft draft,
            String attributeId,
            IncidentAttributeValue value
    ) {
        IncidentAttributeDefinition definition = question(draft, attributeId);
        if (!definition.accepts(value)) {
            throw new IllegalArgumentException("Answer does not match the selected Policy v2 question");
        }
        return draft.answer(definition.id(), value);
    }

    public PolicyV2ManualReview review(Actor actor, PolicyV2ManualDraft draft) {
        requireAccess(actor);
        requireReadyDraft(draft);
        PolicySnapshot snapshot = currentSnapshot();
        validateConfiguredSelection(snapshot, draft);
        IncidentFinding finding = finding(draft);
        List<BehavioralHistoryEntry> history = histories.completeHistory(draft.targetId(), draft.incidentAt());
        PolicyResolution resolution = resolver.resolve(snapshot, finding, draft.incidentAt(), history);
        return new PolicyV2ManualReview(
                draft,
                snapshot,
                finding,
                history,
                resolution,
                route(actor, draft, resolution)
        );
    }

    public SubmissionResult submitShadow(
            Actor actor,
            PolicyV2ManualReview reviewed,
            String operationKey
    ) {
        if (!mayStart(actor)) {
            return new SubmissionResult.Rejected("Punishment authority changed before confirmation");
        }
        if (reviewed == null) {
            return new SubmissionResult.Rejected("A reviewed Policy v2 incident is required");
        }
        String normalizedKey = operationKey(operationKey);
        PolicyV2ManualReview refreshed;
        try {
            refreshed = review(actor, reviewed.draft());
        } catch (IllegalArgumentException exception) {
            return new SubmissionResult.Rejected("Policy changed; reopen the incident before confirming");
        }
        if (!sameEvaluation(reviewed, refreshed)) {
            return new SubmissionResult.Stale(refreshed);
        }
        return recordShadow(refreshed, normalizedKey);
    }

    private SubmissionResult recordShadow(PolicyV2ManualReview review, String operationKey) {
        try {
            UUID evaluationId = recorder.record(review, operationKey, clock.instant());
            return new SubmissionResult.Recorded(evaluationId, review.route());
        } catch (PolicyV2Store.Conflict conflict) {
            return new SubmissionResult.Conflict("The shadow evaluation conflicts with an existing operation");
        }
    }

    private IncidentAttributeDefinition question(PolicyV2ManualDraft draft, String attributeId) {
        if (attributeId == null || attributeId.isBlank()) {
            throw new IllegalArgumentException("Policy v2 question must be present");
        }
        return questions(draft).stream()
                .filter(definition -> definition.id().equals(attributeId.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Question is not relevant to the selected Policy v2 offense"
                ));
    }

    private static IncidentFinding finding(PolicyV2ManualDraft draft) {
        if (draft.isPolicyGap()) {
            return new IncidentFinding(POLICY_GAP_OFFENSE, java.util.Map.of());
        }
        return new IncidentFinding(draft.offenseId().orElseThrow(), draft.attributes());
    }

    private PolicyV2ManualReview.ApprovalRoute route(
            Actor actor,
            PolicyV2ManualDraft draft,
            PolicyResolution resolution
    ) {
        if (draft.isPolicyGap() || resolution.requiresReview()) {
            return PolicyV2ManualReview.ApprovalRoute.ADMIN_FOUNDER_REVIEW;
        }
        if (resolution.action() instanceof PolicyAction.Bounded bounded
                && !actor.rank().atLeast(bounded.minimumRank())) {
            requireRequestAuthority(actor);
            return PolicyV2ManualReview.ApprovalRoute.APPROVAL_REQUIRED;
        }
        if (authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)) {
            return PolicyV2ManualReview.ApprovalRoute.DIRECT_CONFIRM;
        }
        requireRequestAuthority(actor);
        return PolicyV2ManualReview.ApprovalRoute.APPROVAL_REQUIRED;
    }

    private void requireRequestAuthority(Actor actor) {
        if (!authorization.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION)) {
            throw new SecurityException("Actor may not request the selected Policy v2 outcome");
        }
    }

    private void requireAccess(Actor actor) {
        if (!mayStart(actor)) {
            throw new SecurityException("Actor may not use the Policy v2 punishment workflow");
        }
    }

    private static void requireReadyDraft(PolicyV2ManualDraft draft) {
        if (draft == null || draft.categoryId().isEmpty()) {
            throw new IllegalArgumentException("Select a Policy v2 category before review");
        }
        if (draft.isPolicyGap()) {
            if (draft.policyGapSummary().isEmpty()) {
                throw new IllegalArgumentException("Policy Gap requires a description of what happened");
            }
            return;
        }
        if (draft.offenseId().isEmpty()) {
            throw new IllegalArgumentException("Select exact conduct before Policy v2 review");
        }
    }

    private static void validateConfiguredSelection(
            PolicySnapshot snapshot,
            PolicyV2ManualDraft draft
    ) {
        if (draft.isPolicyGap()) {
            return;
        }
        OffensePolicy offense = snapshot.offense(draft.offenseId().orElseThrow()).orElse(null);
        if (offense == null) {
            return;
        }
        PolicyV2Category category = selectedCategory(draft);
        if (category == null || !category.matchesNavigationId(offense.navigationGroupId())) {
            throw new IllegalArgumentException("Selected conduct does not belong to the selected category");
        }
        validateAnswers(offense, draft);
    }

    private static void validateAnswers(OffensePolicy offense, PolicyV2ManualDraft draft) {
        boolean undeclared = draft.attributes().keySet().stream().anyMatch(id -> offense.attributes().stream()
                .noneMatch(definition -> definition.id().equals(id)));
        boolean invalid = offense.attributes().stream().anyMatch(definition -> {
            IncidentAttributeValue value = draft.attributes().get(definition.id());
            return value == null ? definition.required() : !definition.accepts(value);
        });
        if (undeclared || invalid) {
            throw new IllegalArgumentException("Complete only the relevant required Policy v2 questions");
        }
    }

    private static PolicyV2Category selectedCategory(PolicyV2ManualDraft draft) {
        return draft.categoryId().map(PolicyV2Category::byId).orElse(null);
    }

    private static boolean sameEvaluation(
            PolicyV2ManualReview original,
            PolicyV2ManualReview refreshed
    ) {
        return original.snapshot().equals(refreshed.snapshot())
                && original.finding().equals(refreshed.finding())
                && original.historyInputs().equals(refreshed.historyInputs())
                && original.resolution().equals(refreshed.resolution())
                && original.route() == refreshed.route();
    }

    private PolicySnapshot currentSnapshot() {
        PolicySnapshot snapshot = snapshots.get();
        if (snapshot == null) {
            throw new IllegalStateException("Policy v2 snapshot is unavailable");
        }
        return snapshot;
    }

    private static String operationKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > MAX_OPERATION_KEY) {
            throw new IllegalArgumentException("Policy v2 operation key must contain 1-128 characters");
        }
        return value.trim();
    }
}
