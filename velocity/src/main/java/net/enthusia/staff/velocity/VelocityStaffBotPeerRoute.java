package net.enthusia.staff.velocity;

import java.util.Objects;

/** Fixed proxy peer fence: TEST never falls through to the production StaffBot. */
final class VelocityStaffBotPeerRoute {
    static final String TEST_BACKEND = "TEST";
    static final String TEST_PEER = "STAFFBOT_TEST";

    private VelocityStaffBotPeerRoute() {
    }

    static String target(String sourceServerId) {
        Objects.requireNonNull(sourceServerId, "sourceServerId");
        return TEST_BACKEND.equals(sourceServerId)
                ? TEST_PEER : VelocityStaffBotChatSink.PEER_ID;
    }

    static boolean isStaffBotPeer(String peerId) {
        return VelocityStaffBotChatSink.PEER_ID.equals(peerId) || TEST_PEER.equals(peerId);
    }
}
