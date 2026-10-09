package net.enthusia.staff.paper.visibility;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pending durable reads are scoped to an operation, not merely a player UUID. */
final class VanishRecoveryFence {
    private final Map<UUID, Ticket> pending = new ConcurrentHashMap<>();

    Optional<Ticket> begin(UUID playerId) {
        Ticket ticket = new Ticket(Objects.requireNonNull(playerId, "playerId"), UUID.randomUUID());
        return pending.putIfAbsent(playerId, ticket) == null ? Optional.of(ticket) : Optional.empty();
    }

    boolean current(Ticket ticket) {
        return ticket.equals(pending.get(ticket.playerId()));
    }

    void finish(Ticket ticket) {
        pending.remove(ticket.playerId(), ticket);
    }

    void invalidate(UUID playerId) {
        pending.remove(playerId);
    }

    void clear() {
        pending.clear();
    }

    record Ticket(UUID playerId, UUID readId) { }
}
