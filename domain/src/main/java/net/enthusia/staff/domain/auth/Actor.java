package net.enthusia.staff.domain.auth;

import java.util.UUID;

public record Actor(UUID id, String displayName, StaffRank rank) {
    public Actor {
        if (id == null || displayName == null || displayName.isBlank() || rank == null) {
            throw new IllegalArgumentException("actor fields must be present");
        }
    }
}
