package net.enthusia.staff.protocol;

@FunctionalInterface
public interface ChannelMessageHandler {
    boolean handle(ProtocolEnvelope envelope);
}
