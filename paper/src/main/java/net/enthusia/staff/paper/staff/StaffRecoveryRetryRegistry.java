package net.enthusia.staff.paper.staff;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class StaffRecoveryRetryRegistry {
    private final Map<UUID, UUID> tickets = new ConcurrentHashMap<>();

    Optional<UUID> begin(UUID playerId) {
        UUID ticket = UUID.randomUUID();
        return tickets.putIfAbsent(playerId, ticket) == null ? Optional.of(ticket) : Optional.empty();
    }

    boolean consume(UUID playerId, UUID ticket) {
        return tickets.remove(playerId, ticket);
    }

    void clear(UUID playerId) {
        tickets.remove(playerId);
    }
}
