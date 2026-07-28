package net.enthusia.staff.domain.inventory;

import java.util.Objects;
import java.util.Optional;

public record InventoryConfiscationStart(
        Status status,
        Optional<InventoryConfiscationSession> session,
        String detail
) {
    public InventoryConfiscationStart {
        Objects.requireNonNull(status, "status");
        session = Objects.requireNonNull(session, "session");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == Status.LOCKED || status == Status.REPLAYED) != session.isPresent()) {
            throw new IllegalArgumentException("locked and replayed starts require a session");
        }
    }

    public enum Status {
        LOCKED,
        REPLAYED,
        CONFLICT
    }
}
