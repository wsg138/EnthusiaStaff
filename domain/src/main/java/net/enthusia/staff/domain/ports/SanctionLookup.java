package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;

public interface SanctionLookup {
    List<ActiveSanction> activeFor(UUID playerId, Set<SanctionType> types, Instant now);
}
