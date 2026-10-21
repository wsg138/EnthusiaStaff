package net.enthusia.staff.velocity;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Separates authenticated auxiliary peers from durable Paper backend membership. */
final class VelocityChannelPeerPolicy {
    private static final String STAFF_BOT_PROXY_ID = "VELOCITY";

    private VelocityChannelPeerPolicy() {
    }

    static Set<String> paperBackendIds(Set<String> authenticatedPeerIds) {
        Objects.requireNonNull(authenticatedPeerIds, "authenticatedPeerIds");
        Set<String> paperBackends = new LinkedHashSet<>(authenticatedPeerIds);
        paperBackends.remove(VelocityStaffBotChatSink.PEER_ID);
        paperBackends.remove(VelocityStaffBotPeerRoute.TEST_PEER);
        return Set.copyOf(paperBackends);
    }

    static Set<String> requiredPaperBackends(Set<String> authenticatedPeerIds) {
        Set<String> required = paperBackendIds(authenticatedPeerIds);
        if (required.isEmpty()) {
            throw new IllegalArgumentException("at least one Paper backend peer is required");
        }
        return required;
    }

    static boolean allPaperBackendsConnected(
            Set<String> authenticatedPeerIds,
            Set<String> connectedPeerIds
    ) {
        Objects.requireNonNull(connectedPeerIds, "connectedPeerIds");
        Set<String> required = paperBackendIds(authenticatedPeerIds);
        return !required.isEmpty() && connectedPeerIds.containsAll(required);
    }

    static void validateProxyIdentity(String proxyId, Set<String> authenticatedPeerIds) {
        Objects.requireNonNull(proxyId, "proxyId");
        Objects.requireNonNull(authenticatedPeerIds, "authenticatedPeerIds");
        if (authenticatedPeerIds.stream().anyMatch(VelocityStaffBotPeerRoute::isStaffBotPeer)
                && !STAFF_BOT_PROXY_ID.equals(proxyId)) {
            throw new IllegalArgumentException("StaffBot peer requires proxy id VELOCITY");
        }
    }
}
