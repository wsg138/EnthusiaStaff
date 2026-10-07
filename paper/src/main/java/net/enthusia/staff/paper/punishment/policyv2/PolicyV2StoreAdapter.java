package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1BehavioralHistorySource;
import net.enthusia.staff.domain.policyv2.legacy.PolicyV1HistoryCarryForward;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

final class PolicyV2StoreAdapter implements
        PolicyV2ManualWorkflow.HistorySource,
        PolicyV2ManualWorkflow.ShadowRecorder {

    private final Supplier<PolicyV2Store> stores;
    private final Supplier<PolicyV1BehavioralHistorySource> legacyHistorySources;
    private final PolicyV1HistoryCarryForward carryForward;

    PolicyV2StoreAdapter(Supplier<PolicyV2Store> stores) {
        this(stores, () -> PolicyV1BehavioralHistorySource.empty(), new PolicyV1HistoryCarryForward());
    }

    PolicyV2StoreAdapter(
            Supplier<PolicyV2Store> stores,
            Supplier<PolicyV1BehavioralHistorySource> legacyHistorySources
    ) {
        this(stores, legacyHistorySources, new PolicyV1HistoryCarryForward());
    }

    PolicyV2StoreAdapter(
            Supplier<PolicyV2Store> stores,
            Supplier<PolicyV1BehavioralHistorySource> legacyHistorySources,
            PolicyV1HistoryCarryForward carryForward
    ) {
        this.stores = java.util.Objects.requireNonNull(stores, "stores");
        this.legacyHistorySources = java.util.Objects.requireNonNull(legacyHistorySources, "legacyHistorySources");
        this.carryForward = java.util.Objects.requireNonNull(carryForward, "carryForward");
    }

    @Override
    public List<BehavioralHistoryEntry> completeHistory(UUID subjectId, Instant asOf) {
        List<BehavioralHistoryEntry> combined = new ArrayList<>(
                requireStore().completeHistory(subjectId, asOf)
        );
        PolicyV1BehavioralHistorySource legacy = requireLegacyHistorySource();
        combined.addAll(carryForward.convertAll(legacy.completeHistory(subjectId, asOf)));
        combined.sort(Comparator.comparing(BehavioralHistoryEntry::occurredAt)
                .thenComparing(BehavioralHistoryEntry::caseId));
        return List.copyOf(combined);
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

    private PolicyV1BehavioralHistorySource requireLegacyHistorySource() {
        PolicyV1BehavioralHistorySource source = legacyHistorySources.get();
        if (source == null) {
            throw new IllegalStateException("Policy v1 history compatibility source is unavailable");
        }
        return source;
    }
}
