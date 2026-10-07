package net.enthusia.staff.domain.policyv2.publicview;

import java.time.Instant;
import java.util.Objects;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;

/**
 * Single orchestration boundary for turning a sanitized W3C source into the
 * durable canonical public projection.
 */
public final class PolicyV2PublicProjectionMaterializer {
    private final PolicyV2Store store;
    private final PolicyV2PublicProjector projector;

    public PolicyV2PublicProjectionMaterializer(PolicyV2Store store) {
        this(store, new PolicyV2PublicProjector());
    }

    PolicyV2PublicProjectionMaterializer(
            PolicyV2Store store,
            PolicyV2PublicProjector projector
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.projector = Objects.requireNonNull(projector, "projector");
    }

    public PolicyV2PublicProjection materialize(
            PolicyV2PublicProjector.Source source,
            long expectedRevision,
            String operationKey,
            Instant occurredAt
    ) {
        Objects.requireNonNull(source, "source");
        if (expectedRevision < -1 || source.revision() != expectedRevision + 1L) {
            throw new IllegalArgumentException("public projection revision must advance exactly once");
        }
        PolicyV2PublicProjection projection = projector.project(source);
        return store.publishProjection(new PolicyV2Store.PublishProjectionRequest(
                projection,
                expectedRevision,
                operationKey,
                Objects.requireNonNull(occurredAt, "occurredAt")
        ));
    }
}
