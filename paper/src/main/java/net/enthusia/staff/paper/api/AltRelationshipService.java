package net.enthusia.staff.paper.api;

import java.util.Optional;
import java.util.UUID;

public interface AltRelationshipService {
    Optional<String> relationship(UUID first, UUID second);
}
