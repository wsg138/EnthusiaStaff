package net.enthusia.staff.velocity;

import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Map;
import javax.crypto.SecretKey;
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

    static void validateIndependentStaffBotSecrets(Map<String, SecretKey> keys) {
        Objects.requireNonNull(keys, "keys");
        SecretKey isolated = keys.get(VelocityStaffBotPeerRoute.TEST_PEER);
        if (isolated == null) {
            return;
        }
        byte[] isolatedBytes = Objects.requireNonNull(isolated.getEncoded(), "isolated key bytes");
        try {
            for (Map.Entry<String, SecretKey> entry : keys.entrySet()) {
                if (VelocityStaffBotPeerRoute.TEST_PEER.equals(entry.getKey())) {
                    continue;
                }
                byte[] other = Objects.requireNonNull(entry.getValue().getEncoded(), "peer key bytes");
                try {
                    if (MessageDigest.isEqual(isolatedBytes, other)) {
                        throw new IllegalArgumentException(
                                "isolated TEST StaffBot peer must have a unique secret");
                    }
                } finally {
                    java.util.Arrays.fill(other, (byte) 0);
                }
            }
        } finally {
            java.util.Arrays.fill(isolatedBytes, (byte) 0);
        }
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
