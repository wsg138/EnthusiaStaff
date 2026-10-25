package net.enthusia.staff.paper.sanction;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.inventory.Inventory;

/**
 * Inventory-origin fence for queued /unban and sanction lookup results.
 * A result may open a GUI only while its original inventory remains visible
 * and it is still the last requested lookup for that viewer.
 */
final class SanctionChangeGuiLookupFence {
    private final ConcurrentMap<UUID, Ticket> pending = new ConcurrentHashMap<>();

    static final class Ticket {
        private final UUID viewerId;
        private final Inventory origin;

        private Ticket(UUID viewerId, Inventory origin) {
            this.viewerId = viewerId;
            this.origin = origin;
        }
    }

    Ticket begin(UUID viewerId, Inventory origin) {
        if (viewerId == null || origin == null) {
            throw new IllegalArgumentException("Sanction lookup needs viewer and source inventory");
        }
        Ticket ticket = new Ticket(viewerId, origin);
        pending.put(viewerId, ticket);
        return ticket;
    }

    void cancel(UUID viewerId) {
        pending.remove(viewerId);
    }

    void cancel(UUID viewerId, Ticket ticket) {
        pending.remove(viewerId, ticket);
    }

    boolean consume(UUID viewerId, Ticket ticket, Inventory current) {
        if (ticket == null || viewerId == null || current == null
                || !viewerId.equals(ticket.viewerId)
                || ticket.origin != current) {
            return false;
        }
        return pending.remove(viewerId, ticket);
    }
}
