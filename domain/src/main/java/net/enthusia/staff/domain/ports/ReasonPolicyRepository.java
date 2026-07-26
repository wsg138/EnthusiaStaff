package net.enthusia.staff.domain.ports;

import java.util.Collection;
import java.util.Optional;
import net.enthusia.staff.domain.escalation.ReasonPolicy;

public interface ReasonPolicyRepository {
    Optional<ReasonPolicy> find(String reasonId);

    Collection<ReasonPolicy> all();

    String activeVersion();
}
