package net.enthusia.staff.domain.alt;

public record NetworkIdentityObservationResult(
        int matchedPlayers,
        int inheritedSanctions,
        int alertsCreated,
        boolean evidenceSuppressed
) {
    public NetworkIdentityObservationResult {
        if (matchedPlayers < 0 || inheritedSanctions < 0 || alertsCreated < 0) {
            throw new IllegalArgumentException("network identity observation counts cannot be negative");
        }
    }
}
