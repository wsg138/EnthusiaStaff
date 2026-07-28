package net.enthusia.staff.domain.ports;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.network.NetworkOutboxMessage;

public interface NetworkOutboxStore {
    List<NetworkOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now);

    void prepareDeliveries(UUID messageId, Collection<String> serverIds);

    Set<String> pendingDestinations(UUID messageId);

    void acknowledgeDelivery(UUID messageId, String serverId, Instant now);

    boolean complete(UUID messageId, String owner, Instant now);

    void retry(UUID messageId, String owner, Instant availableAt, String errorCode);

    void deadLetter(UUID messageId, String owner, String errorCode);

    boolean recordInboxOnce(String consumerId, UUID messageId, String messageType, String outcomeJson, Instant now);
}
