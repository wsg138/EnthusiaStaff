package net.enthusia.staff.domain.inventory;

import java.util.Objects;
import java.util.Optional;

public record InventoryPreparation(Status status, Optional<InventoryPatch> patch, String detail) {
    public InventoryPreparation {
        Objects.requireNonNull(status, "status");
        patch = Objects.requireNonNull(patch, "patch");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == Status.PREPARED || status == Status.REPLAYED) != patch.isPresent()) {
            throw new IllegalArgumentException("prepared and replayed results require a patch");
        }
    }

    public enum Status {
        PREPARED,
        REPLAYED,
        STALE,
        LOCKED,
        PLAYER_ONLINE,
        PROFILE_NOT_FOUND
    }
}
