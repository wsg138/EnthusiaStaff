package net.enthusia.staff.paper.config.reload;

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
        AtomicReasonPolicyRepository.PolicySnapshot snapshot = repository.snapshot();
        return new Snapshot(
                snapshot.version(),
                snapshot.policies(),
                snapshot.aliases(),
                snapshot.removedReasons()
        );
    }

    @Override
    public void publish(ReasonPolicyConfigurationLoader.LoadedPolicies policies) {
        Objects.requireNonNull(policies, "policies");
        repository.replace(
                policies.version(),
                policies.policies(),
                policies.aliases(),
                policies.removedReasons()
        );
    }

    @Override
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        repository.replace(
                snapshot.version(),
                snapshot.policies(),
                snapshot.aliases(),
                snapshot.removedReasons()
        );
    }
}
