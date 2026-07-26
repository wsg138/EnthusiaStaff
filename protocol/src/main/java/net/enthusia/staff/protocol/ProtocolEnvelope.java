package net.enthusia.staff.protocol;

import java.util.UUID;
import net.enthusia.staff.common.Checks;

public record ProtocolEnvelope(
        int protocolVersion,
        UUID messageId,
        String serverId,
        String messageType,
        long timestampEpochMillis,
        String nonce,
        String payloadJson,
        String mac
) {
    public ProtocolEnvelope {
        if (protocolVersion < 1 || messageId == null) {
            throw new IllegalArgumentException("invalid protocol version or message ID");
        }
        serverId = Checks.nonBlank(serverId, "serverId", 64);
        messageType = Checks.nonBlank(messageType, "messageType", 64);
        nonce = Checks.nonBlank(nonce, "nonce", 96);
        payloadJson = Checks.nonBlank(payloadJson, "payloadJson", 1_000_000);
        mac = Checks.nonBlank(mac, "mac", 128);
    }
}
