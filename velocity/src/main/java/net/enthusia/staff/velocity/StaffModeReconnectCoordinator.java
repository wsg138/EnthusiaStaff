package net.enthusia.staff.velocity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;

final class StaffModeReconnectCoordinator {
    private static final Duration TTL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    void remember(UUID playerId, StaffSessionSnapshot session, String requested, Instant now) {
        if (session.state() != StaffSessionState.ACTIVE
                || session.serverId().equalsIgnoreCase(requested)) {
            pending.remove(playerId);
            return;
        }
        pending.put(playerId, new Pending(
                session.serverId(), requested, session.sessionId(), now.plus(TTL)));
    }

    Optional<String> claimDestination(
            UUID playerId,
            UUID sessionId,
            String readyOwner,
            String currentServer,
            Instant now
    ) {
        Pending intent = pending.get(playerId);
        if (intent == null) {
            return Optional.empty();
        }
        if (!intent.expiresAt().isAfter(now)) {
            pending.remove(playerId, intent);
            return Optional.empty();
        }
        if (!matches(intent, sessionId, readyOwner, currentServer)) {
            return Optional.empty();
        }
        return pending.remove(playerId, intent)
                ? Optional.of(intent.destination())
                : Optional.empty();
    }

    void disconnected(UUID playerId) {
        pending.remove(playerId);
    }

    private static boolean matches(
            Pending intent,
            UUID sessionId,
            String readyOwner,
            String currentServer
    ) {
        return intent.sessionId().equals(sessionId)
                && intent.owner().equalsIgnoreCase(readyOwner)
                && intent.owner().equalsIgnoreCase(currentServer);
    }

    private record Pending(String owner, String destination, UUID sessionId, Instant expiresAt) {
    }
}
