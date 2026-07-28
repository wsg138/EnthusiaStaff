package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;

public interface OperationalStateStore {
    OperationalStateSnapshot current();

    boolean transition(long expectedRevision, OperationalMode next, UUID actorId, String reason, Instant now);

    boolean hasAuthorizedCutover();
}
