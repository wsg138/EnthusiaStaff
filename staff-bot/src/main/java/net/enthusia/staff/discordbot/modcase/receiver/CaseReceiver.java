package net.enthusia.staff.discordbot.modcase.receiver;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Inactive receiver facade. Peer identity MUST come from a future authenticated transport. */
public final class CaseReceiver {
    public record Peer(boolean verified, String originPlatform, String scopeId, long guildId) { }
    private final ReceiverJournal journal;
    private final String origin;
    private final String scope;
    private final long privateGuild;

    public CaseReceiver(ReceiverJournal journal, String origin, String scope, long privateGuild) {
        if (!java.util.Set.of("minecraft", "discord").contains(origin)
                || scope == null || !scope.matches("[A-Za-z0-9_:/.-]{1,160}")
                || privateGuild <= 0) {
            throw new IllegalArgumentException("receiver requires exact private binding");
        }
        this.journal = Objects.requireNonNull(journal);
        this.origin = origin;
        this.scope = scope;
        this.privateGuild = privateGuild;
    }

    public Optional<CaseAck> receive(Peer peer, String wire) {
        if (!authorized(peer)) {
            return Optional.empty();
        }
        try {
            CaseEvent event = CaseEvent.parse(wire);
            if (!matches(event)) {
                return Optional.empty();
            }
            ReceiverJournal.Outcome result = journal.accept(event);
            return Optional.of(new CaseAck(result.name(), event.operationId(), event.eventType(),
                    event.caseId(), event.revision(), event.originPlatform(), event.scopeId()));
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            return Optional.empty(); // Unknown commit/validation outcome cannot become ACCEPTED.
        }
    }

    /** Only a separately verified *current* canonical snapshot can clear resync. */
    public boolean reconcile(Peer peer, CaseEvent currentAuthoritative) {
        if (!authorized(peer) || currentAuthoritative == null || !matches(currentAuthoritative)) {
            return false;
        }
        try {
            return journal.reconcile(currentAuthoritative);
        } catch (IOException | IllegalArgumentException error) {
            return false;
        }
    }

    private boolean matches(CaseEvent event) {
        return origin.equals(event.originPlatform()) && scope.equals(event.scopeId());
    }

    private boolean authorized(Peer peer) {
        return peer != null && peer.verified() && origin.equals(peer.originPlatform())
                && scope.equals(peer.scopeId()) && privateGuild == peer.guildId();
    }
}
