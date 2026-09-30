package net.enthusia.staff.domain.commandbridge;

/** Safe response DTO. Raw transport errors, credentials, persistence values, and unbounded output never belong here. */
public record CommandBridgeResponse(
        CommandBridgeOutcome outcome,
        String message,
        String output,
        boolean truncated,
        boolean redacted
) {
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MAX_OUTPUT_LENGTH = 2_000;

    public CommandBridgeResponse {
        if (outcome == null || message == null || output == null
                || message.length() > MAX_MESSAGE_LENGTH || output.length() > MAX_OUTPUT_LENGTH) {
            throw new IllegalArgumentException("command bridge response is invalid");
        }
    }

    public static CommandBridgeResponse withoutOutput(CommandBridgeOutcome outcome, String message) {
        return new CommandBridgeResponse(outcome, message, "", false, false);
    }
}
