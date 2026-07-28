package net.enthusia.staff.protocol;

import java.util.UUID;

public record UnsignedEnvelope(
        int protocolVersion,
        UUID messageId,
        String serverId,
        String messageType,
        long timestampEpochMillis,
        String nonce,
        String payloadJson
) {
    public ProtocolEnvelope withMac(String mac) {
        return new ProtocolEnvelope(
                protocolVersion,
                messageId,
                serverId,
                messageType,
                timestampEpochMillis,
                nonce,
                payloadJson,
                mac
        );
    }
}
