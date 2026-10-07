package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2StoreAdapter implements
        PolicyV2ManualWorkflow.HistorySource,
        PolicyV2ManualWorkflow.ShadowRecorder {

    private final Supplier<PolicyV2Store> stores;

    PolicyV2StoreAdapter(Supplier<PolicyV2Store> stores) {
        this.stores = java.util.Objects.requireNonNull(stores, "stores");
    }

    @Override
    public List<BehavioralHistoryEntry> completeHistory(UUID subjectId, Instant asOf) {
        return requireStore().completeHistory(subjectId, asOf);
    }

    @Override
    public UUID record(
            PolicyV2ManualReview review,
            String operationKey,
            Instant evaluatedAt
    ) {
        PolicyV2Store.ShadowEvaluation result = requireStore().recordShadowEvaluation(
                new PolicyV2Store.ShadowEvaluationRequest(
                        review.draft().targetId(),
                        Optional.empty(),
                        review.snapshot(),
                        review.finding(),
                        review.resolution(),
                        review.historyInputs(),
                        operationKey,
                        evaluatedAt
                )
        );
        return result.evaluationId();
    }

    private PolicyV2Store requireStore() {
        PolicyV2Store store = stores.get();
        if (store == null) {
            throw new IllegalStateException("Policy v2 persistence is unavailable");
        }
        return store;
    }
}
