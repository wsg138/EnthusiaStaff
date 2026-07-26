package net.enthusia.staff.domain.inventory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ConfiscatedAssetReservation(
        Status status,
        UUID restorationOperationId,
        List<ConfiscatedAssetSnapshot> snapshots,
        String detail
) {
    public ConfiscatedAssetReservation {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(restorationOperationId, "restorationOperationId");
        snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == Status.RESERVED || status == Status.REPLAYED) != !snapshots.isEmpty()) {
            throw new IllegalArgumentException("reserved restoration requires snapshots");
        }
    }

    public enum Status {
        RESERVED,
        REPLAYED,
        NOT_FOUND,
        LOCKED
    }
}
