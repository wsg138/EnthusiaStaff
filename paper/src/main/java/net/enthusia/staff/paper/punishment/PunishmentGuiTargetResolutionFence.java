package net.enthusia.staff.paper.punishment;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Prevents a delayed player-directory lookup from opening a punishment GUI
 * after newer navigation or after the player has switched inventories.
 *
 * Tokens are consumed on the entity thread immediately before presentation.
 */
final class PunishmentGuiTargetResolutionFence {
    private final ConcurrentMap<UUID, Ticket> pending = new ConcurrentHashMap<>();

    record Ticket(Object nonce, Object sourceInventory) {
        Ticket {
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(sourceInventory, "sourceInventory");
        }
    }

    Ticket begin(UUID viewerId, Object sourceInventory) {
        Ticket ticket = new Ticket(new Object(), sourceInventory);
        pending.put(viewerId, ticket);
        return ticket;
    }

    boolean consume(UUID viewerId, Ticket ticket, Object currentInventory) {
        // Consume even when the player switched to a different inventory.
        // Never allow an old callback to consume a newer request's ticket.
        return pending.remove(viewerId, ticket)
                && ticket.sourceInventory() == currentInventory;
    }

    boolean cancel(UUID viewerId, Ticket ticket) {
        return pending.remove(viewerId, ticket);
    }

    void invalidate(UUID viewerId) {
        pending.remove(viewerId);
    }
}
