package net.enthusia.staff.paper.staff;

import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.GameMode;

/** Pure suitability checks for random-player teleport targets. */
final class StaffToolTargetPolicy {
    private StaffToolTargetPolicy() {
    }

    static boolean eligibleRandomTarget(Candidate candidate) {
        java.util.Objects.requireNonNull(candidate, "candidate");
        return candidate.identity().eligible()
                && candidate.state().eligible()
                && candidate.environment().eligible();
    }

    record Candidate(Identity identity, State state, Environment environment) {
        Candidate {
            java.util.Objects.requireNonNull(identity, "identity");
            java.util.Objects.requireNonNull(state, "state");
            java.util.Objects.requireNonNull(environment, "environment");
        }
    }

    record Identity(UUID actorId, UUID targetId) {
        boolean eligible() {
            return actorId != null && targetId != null && !actorId.equals(targetId);
        }
    }

    record State(
            boolean staffModeActive,
            boolean vanished,
            boolean frozen,
            boolean exempt,
            boolean dead,
            boolean sleeping,
            boolean insideVehicle
    ) {
        boolean eligible() {
            return Stream.of(staffModeActive, vanished, frozen, exempt, dead, sleeping, insideVehicle)
                    .noneMatch(Boolean::booleanValue);
        }
    }

    record Environment(GameMode gameMode, boolean worldEnabled) {
        boolean eligible() {
            return worldEnabled && gameMode != null && gameMode != GameMode.SPECTATOR;
        }
    }
}
