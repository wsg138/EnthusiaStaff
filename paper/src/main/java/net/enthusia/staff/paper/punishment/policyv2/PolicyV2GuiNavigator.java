package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.OffensePolicy;

final class PolicyV2GuiNavigator {
    private static final String POLICY_GAP_LABEL = "Policy Gap / Unclassified Incident";

    private final PolicyV2ManualWorkflow workflow;

    PolicyV2GuiNavigator(PolicyV2ManualWorkflow workflow) {
        this.workflow = java.util.Objects.requireNonNull(workflow, "workflow");
    }

    PolicyV2GuiState.Categories start(
            UUID viewerId,
            UUID targetId,
            String targetName,
            Instant incidentAt
    ) {
        PolicyV2ManualDraft draft = PolicyV2ManualDraft.start(targetId, incidentAt);
        return categories(viewerId, targetName, draft);
    }

    PolicyV2GuiState selectCategory(PolicyV2GuiState.Categories state, int index) {
        if (index < 0 || index >= state.categories().size()) {
            throw new IllegalArgumentException("Policy v2 category selection is outside the visible list");
        }
        PolicyV2Category category = state.categories().get(index);
        PolicyV2ManualDraft draft = state.draft().selectCategory(category);
        if (category.reviewOnly()) {
            return questions(state.viewerId(), state.targetName(), draft, POLICY_GAP_LABEL);
        }
        return offenses(state.viewerId(), state.targetName(), draft, category, 0);
    }

    PolicyV2GuiState.Questions selectOffense(PolicyV2GuiState.Offenses state, int index) {
        if (index < 0 || index >= state.offenses().size()) {
            throw new IllegalArgumentException("Policy v2 offense selection is outside the visible list");
        }
        OffensePolicy offense = state.offenses().get(index);
        PolicyV2ManualDraft draft = state.draft().selectOffense(offense.id());
        return questions(state.viewerId(), state.targetName(), draft, offense.displayName());
    }

    PolicyV2GuiState.Questions withDraft(
            PolicyV2GuiState.Questions state,
            PolicyV2ManualDraft draft
    ) {
        return new PolicyV2GuiState.Questions(
                state.viewerId(), state.targetId(), state.targetName(),
                draft, state.conductLabel(), workflow.questions(draft)
        );
    }

    PolicyV2GuiState.Review review(PolicyV2GuiState.Questions state) {
        requireReady(state);
        return new PolicyV2GuiState.Review(
                state.viewerId(), state.targetId(), state.targetName(),
                state.draft(), state.conductLabel()
        );
    }

    PolicyV2GuiState.Result result(
            PolicyV2GuiState.Review state,
            PolicyV2ManualReview review,
            UUID operationId
    ) {
        return new PolicyV2GuiState.Result(
                state.viewerId(), state.targetId(), state.targetName(),
                review, PolicyV2ReviewPresentation.from(review), operationId
        );
    }

    PolicyV2GuiState.Result refresh(
            PolicyV2GuiState.Result state,
            PolicyV2ManualReview review
    ) {
        return new PolicyV2GuiState.Result(
                state.viewerId(), state.targetId(), state.targetName(),
                review, PolicyV2ReviewPresentation.from(review), state.operationId()
        );
    }

    Optional<PolicyV2GuiState> back(PolicyV2GuiState state) {
        if (state instanceof PolicyV2GuiState.Categories) {
            return Optional.empty();
        }
        if (state instanceof PolicyV2GuiState.Offenses offenses) {
            return Optional.of(categories(offenses.viewerId(), offenses.targetName(), offenses.draft().editCategory()));
        }
        if (state instanceof PolicyV2GuiState.Questions questions) {
            return Optional.of(backFromQuestions(questions));
        }
        if (state instanceof PolicyV2GuiState.Review review) {
            return Optional.of(questions(
                    review.viewerId(), review.targetName(), review.draft(), review.conductLabel()
            ));
        }
        PolicyV2GuiState.Result result = (PolicyV2GuiState.Result) state;
        return Optional.of(questions(
                result.viewerId(), result.targetName(), result.review().draft(),
                conductLabel(result.review().draft())
        ));
    }

    private PolicyV2GuiState backFromQuestions(PolicyV2GuiState.Questions state) {
        if (state.draft().isPolicyGap()) {
            return categories(state.viewerId(), state.targetName(), state.draft().editCategory());
        }
        PolicyV2ManualDraft edited = state.draft().editOffense();
        PolicyV2Category category = selectedCategory(edited);
        return offenses(state.viewerId(), state.targetName(), edited, category, 0);
    }

    private PolicyV2GuiState.Categories categories(
            UUID viewerId,
            String targetName,
            PolicyV2ManualDraft draft
    ) {
        return new PolicyV2GuiState.Categories(
                viewerId, draft.targetId(), targetName, draft, workflow.categories()
        );
    }

    private PolicyV2GuiState.Offenses offenses(
            UUID viewerId,
            String targetName,
            PolicyV2ManualDraft draft,
            PolicyV2Category category,
            int page
    ) {
        if (category == null || category.reviewOnly()) {
            throw new IllegalArgumentException("Policy v2 offense category is not selectable");
        }
        return new PolicyV2GuiState.Offenses(
                viewerId, draft.targetId(), targetName, draft, workflow.offenses(category), page
        );
    }

    private PolicyV2GuiState.Questions questions(
            UUID viewerId,
            String targetName,
            PolicyV2ManualDraft draft,
            String conductLabel
    ) {
        return new PolicyV2GuiState.Questions(
                viewerId, draft.targetId(), targetName, draft, conductLabel, workflow.questions(draft)
        );
    }

    private void requireReady(PolicyV2GuiState.Questions state) {
        if (state.draft().isPolicyGap()) {
            if (state.draft().policyGapSummary().isEmpty()) {
                throw new IllegalArgumentException("Describe what happened before reviewing Policy Gap");
            }
            return;
        }
        List<String> missing = state.questions().stream()
                .filter(IncidentAttributeDefinition::required)
                .filter(definition -> !state.draft().attributes().containsKey(definition.id()))
                .map(definition -> PolicyV2ReviewPresentation.humanize(definition.id()))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Answer required questions: " + String.join(", ", missing));
        }
    }

    private String conductLabel(PolicyV2ManualDraft draft) {
        if (draft.isPolicyGap()) {
            return POLICY_GAP_LABEL;
        }
        PolicyV2Category category = selectedCategory(draft);
        return workflow.offenses(category).stream()
                .filter(offense -> offense.id().equals(draft.offenseId().orElse("")))
                .map(OffensePolicy::displayName)
                .findFirst()
                .orElse("Configured conduct");
    }

    private static PolicyV2Category selectedCategory(PolicyV2ManualDraft draft) {
        return draft.categoryId().map(PolicyV2Category::byId).orElse(null);
    }
}
