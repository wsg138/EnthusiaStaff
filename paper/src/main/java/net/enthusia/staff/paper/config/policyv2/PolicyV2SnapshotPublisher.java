package net.enthusia.staff.paper.config.policyv2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;

public final class PolicyV2SnapshotPublisher {
    private final AtomicReference<State> state = new AtomicReference<>(State.disabled());

    public Publication publish(PolicyV2Configuration candidate) {
        java.util.Objects.requireNonNull(candidate, "candidate");
        while (true) {
            State current = state.get();
            State next = merge(current, candidate);
            if (next.samePublication(current)) {
                return publication(current, false);
            }
            if (state.compareAndSet(current, next)) {
                return publication(next, true);
            }
        }
    }

    public boolean shadowEnabled() {
        return state.get().mode() == PolicyV2FeatureMode.SHADOW;
    }

    public PolicyV2FeatureMode mode() {
        return state.get().mode();
    }

    public Optional<String> activeVersion() {
        return Optional.ofNullable(state.get().activeVersion());
    }

    public PolicySnapshot activeSnapshot() {
        State current = state.get();
        if (current.activeVersion() == null) {
            throw new IllegalStateException("Policy v2 has no valid published snapshot");
        }
        PolicySnapshot snapshot = current.snapshots().get(current.activeVersion());
        if (snapshot == null) {
            throw new IllegalStateException("Policy v2 active snapshot is unavailable");
        }
        return snapshot;
    }

    public Optional<PolicySnapshot> snapshot(String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(state.get().snapshots().get(version.trim()));
    }

    public View view() {
        State current = state.get();
        return new View(
                current.mode(),
                Optional.ofNullable(current.activeVersion()),
                current.snapshots().size(),
                current.generation()
        );
    }

    private static State merge(State current, PolicyV2Configuration candidate) {
        Map<String, PolicySnapshot> retained = new LinkedHashMap<>(current.snapshots());
        candidate.snapshots().forEach((version, snapshot) -> {
            PolicySnapshot prior = retained.get(version);
            if (prior != null && !prior.equals(snapshot)) {
                throw new PolicyV2PublicationException(
                        "Policy v2 version collision for " + version + "; existing content was retained"
                );
            }
            retained.putIfAbsent(version, snapshot);
        });
        PolicySnapshot active = retained.get(candidate.activeVersion());
        if (active == null || !active.equals(candidate.activeSnapshot())) {
            throw new PolicyV2PublicationException("Policy v2 active version did not survive publication validation");
        }
        return new State(
                candidate.mode(),
                candidate.activeVersion(),
                Map.copyOf(retained),
                current.generation() + 1
        );
    }

    private static Publication publication(State state, boolean changed) {
        return new Publication(
                changed,
                state.mode(),
                Optional.ofNullable(state.activeVersion()),
                state.snapshots().size(),
                state.generation()
        );
    }

    public record Publication(
            boolean changed,
            PolicyV2FeatureMode mode,
            Optional<String> activeVersion,
            int retainedVersions,
            long generation
    ) {
        public Publication {
            activeVersion = activeVersion == null ? Optional.empty() : activeVersion;
        }
    }

    public record View(
            PolicyV2FeatureMode mode,
            Optional<String> activeVersion,
            int retainedVersions,
            long generation
    ) {
        public View {
            activeVersion = activeVersion == null ? Optional.empty() : activeVersion;
        }
    }

    private record State(
            PolicyV2FeatureMode mode,
            String activeVersion,
            Map<String, PolicySnapshot> snapshots,
            long generation
    ) {
        private State {
            snapshots = Map.copyOf(snapshots);
        }

        static State disabled() {
            return new State(PolicyV2FeatureMode.DISABLED, null, Map.of(), 0);
        }

        boolean samePublication(State other) {
            return mode == other.mode
                    && java.util.Objects.equals(activeVersion, other.activeVersion)
                    && snapshots.equals(other.snapshots);
        }
    }
}
