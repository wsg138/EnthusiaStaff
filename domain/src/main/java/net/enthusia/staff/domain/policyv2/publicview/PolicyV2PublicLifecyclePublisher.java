package net.enthusia.staff.domain.policyv2.publicview;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

/**
 * Rebuilds the canonical public projection after a Policy v2 lifecycle change.
 *
 * <p>This is intentionally the private/public choke point: lifecycle records
 * are read here, projected through the W3C allowlist, and only the resulting
 * {@link PolicyV2PublicProjection} is persisted for public consumers.</p>
 */
public final class PolicyV2PublicLifecyclePublisher {
    private static final int MAX_LIFECYCLE_EVENTS = 500;

    private final PolicyV2Store store;
    private final PolicyV2PublicProjectionMaterializer materializer;

    public PolicyV2PublicLifecyclePublisher(PolicyV2Store store) {
        this.store = Objects.requireNonNull(store, "store");
        this.materializer = new PolicyV2PublicProjectionMaterializer(store);
    }

    public PolicyV2PublicProjection publish(
            String caseId,
            PublicContext context,
            String operationKey,
            Instant occurredAt,
            Instant now
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(now, "now");
        PolicyV2Store.CaseRecord policyCase = store.findCase(caseId)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 case does not exist"));
        Optional<PolicyV2PublicProjection> prior = store.publicProjection(policyCase.caseId());
        long expectedRevision = prior.map(PolicyV2PublicProjection::revision).orElse(-1L);
        return materializer.materialize(
                source(policyCase, context, now, expectedRevision + 1L),
                expectedRevision,
                operationKey,
                occurredAt
        );
    }

    public Optional<PolicyV2PublicProjection> republishExisting(
            String caseId,
            Optional<String> currentPlayerName,
            String operationKey,
            Instant occurredAt,
            Instant now
    ) {
        Optional<PolicyV2PublicProjection> existing = store.publicProjection(caseId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PolicyV2PublicProjection prior = existing.orElseThrow();
        PolicyV2Store.CaseRecord policyCase = store.findCase(caseId)
                .orElseThrow(() -> new PolicyV2Store.MissingRecord("Policy v2 case does not exist"));
        String visibleOffenseId = policyCase.effectiveFinding()
                .map(finding -> finding.offenseId())
                .orElse(policyCase.originalFinding().offenseId());
        PublicContext context = new PublicContext(
                currentPlayerName.isPresent() ? currentPlayerName : prior.currentPlayerName(),
                prior.incidentPlayerName(),
                prior.category(),
                prior.publicReason(),
                prior.relatedHistorySummary(),
                Map.of(visibleOffenseId, prior.publicOffense()),
                prior.appealStatus() == PolicyV2PublicProjection.AppealStatus.AVAILABLE
        );
        return Optional.of(publish(caseId, context, operationKey, occurredAt, now));
    }

    private PolicyV2PublicProjector.Source source(
            PolicyV2Store.CaseRecord policyCase,
            PublicContext context,
            Instant now,
            long revision
    ) {
        return new PolicyV2PublicProjector.Source(
                policyCase.caseId(),
                policyCase.findingState(),
                policyCase.originalFinding().offenseId(),
                policyCase.effectiveFinding().map(finding -> finding.offenseId()),
                policyCase.incidentAt(),
                policyCase.currentSanctions(),
                policyCase.remedies(),
                complete(store.findingRevisions(policyCase.caseId(), MAX_LIFECYCLE_EVENTS), "finding revisions"),
                complete(store.sanctionRevisions(policyCase.caseId(), MAX_LIFECYCLE_EVENTS), "sanction revisions"),
                complete(store.appealHistory(policyCase.caseId(), MAX_LIFECYCLE_EVENTS), "appeal events"),
                context.metadata(),
                context.publicOffenseLabels(),
                policyCase.resolution().policyVersion(),
                now,
                revision
        );
    }

    private static <T> java.util.List<T> complete(java.util.List<T> records, String label) {
        if (records.size() >= MAX_LIFECYCLE_EVENTS) {
            throw new IllegalStateException("Policy v2 public " + label + " reached the safe read bound");
        }
        return records;
    }

    public record PublicContext(
            Optional<String> currentPlayerName,
            Optional<String> incidentPlayerName,
            String category,
            String publicReason,
            Optional<String> relatedHistoryLabel,
            Map<String, String> publicOffenseLabels,
            boolean appealAvailable
    ) {
        public PublicContext {
            if (currentPlayerName == null || incidentPlayerName == null || relatedHistoryLabel == null
                    || publicOffenseLabels == null) {
                throw new IllegalArgumentException("public lifecycle context is invalid");
            }
            publicOffenseLabels = Map.copyOf(publicOffenseLabels);
        }

        PolicyV2PublicProjector.PublicMetadata metadata() {
            return new PolicyV2PublicProjector.PublicMetadata(
                    currentPlayerName,
                    incidentPlayerName,
                    category,
                    publicReason,
                    relatedHistoryLabel,
                    appealAvailable
            );
        }
    }
}
