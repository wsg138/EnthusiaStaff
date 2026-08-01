package net.enthusia.staff.paper.config.reload;

import java.util.List;
import java.util.Objects;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

public final class AtomicReasonPolicyPublisher implements ReasonPolicyPublisher {
    private final AtomicReasonPolicyRepository repository;

    public AtomicReasonPolicyPublisher(AtomicReasonPolicyRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(repository.activeVersion(), List.copyOf(repository.all()));
    }

    @Override
    public void publish(ReasonPolicyConfigurationLoader.LoadedPolicies policies) {
        Objects.requireNonNull(policies, "policies");
        repository.replace(policies.version(), policies.policies());
    }

    @Override
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        repository.replace(snapshot.version(), snapshot.policies());
    }
}
