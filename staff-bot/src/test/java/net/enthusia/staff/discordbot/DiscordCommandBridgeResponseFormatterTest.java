package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.commandbridge.CommandBridgeOutcome;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import org.junit.jupiter.api.Test;

class DiscordCommandBridgeResponseFormatterTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void successOutputCannotCreateDiscordMentionsOrCodeFences() {
        CommandBridgeResponse response = new CommandBridgeResponse(
                CommandBridgeOutcome.SUCCESS,
                "Command executed.",
                "@everyone ``` token=[redacted]",
                false,
                true
        );
        String formatted = DiscordCommandBridgeResponseFormatter.format(new DiscordCommandBridgeCoordinator.Result(
                REQUEST_ID,
                DiscordCommandBridgeCoordinator.Status.RECEIVED,
                Optional.of(response)
        ));

        assertFalse(formatted.contains("@everyone"));
        assertFalse(formatted.contains("```"));
        assertTrue(formatted.contains("redacted"));
    }

    @Test
    void ambiguousFailureExplicitlySaysThereWasNoRetry() {
        String formatted = DiscordCommandBridgeResponseFormatter.format(new DiscordCommandBridgeCoordinator.Result(
                REQUEST_ID,
                DiscordCommandBridgeCoordinator.Status.AMBIGUOUS_FAILURE,
                Optional.empty()
        ));
        assertTrue(formatted.contains("not retried"));
    }
}
