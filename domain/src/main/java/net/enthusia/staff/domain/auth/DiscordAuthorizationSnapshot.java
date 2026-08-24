package net.enthusia.staff.domain.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable confirmation-time authority fingerprint. Final commit must reauthorize this exact
 * request against current actor and target-staff state.
 */
public record DiscordAuthorizationSnapshot(
        UUID actorId,
        StaffRank actorRank,
        Optional<UUID> targetStaffId,
        Optional<StaffRank> targetStaffRank,
        DiscordAuthorizationRequest request
) {
    public DiscordAuthorizationSnapshot {
        if (actorId == null || actorRank == null || targetStaffId == null || targetStaffRank == null || request == null) {
            throw new IllegalArgumentException("snapshot fields must be present");
        }
        if (targetStaffId.isPresent() != targetStaffRank.isPresent()) {
            throw new IllegalArgumentException("target staff id and rank must be captured together");
        }
    }
}
