package net.enthusia.staff.domain.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class CommandBridgePolicyTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final DiscordUserId DISCORD_ID = new DiscordUserId("123456789012345678");
    private static final UUID ACTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");

    private final CommandBridgePolicy policy = new CommandBridgePolicy(
            Set.of("smp"),
            List.of(new CommandBridgeRule("list", StaffRank.MOD, "enthusia.console.list", 0))
    );

    @Test
    void acceptsOnlyExplicitTargetAndCommand() {
        CommandBridgePolicy.Decision decision = policy.evaluate(request("smp", "/LIST"));
        assertTrue(decision.accepted());
        assertEquals("list", decision.commandName());
        assertEquals("LIST", decision.normalizedCommand());
    }

    @Test
    void rejectsUnsupportedCommandAndServer() {
        assertEquals(CommandBridgePolicy.Status.UNSUPPORTED_COMMAND,
                policy.evaluate(request("smp", "stop")).status());
        assertEquals(CommandBridgePolicy.Status.INVALID_SERVER,
                policy.evaluate(request("events", "list")).status());
    }

    @Test
    void rejectsControlCharactersAndExcessArguments() {
        assertEquals(CommandBridgePolicy.Status.MALFORMED_COMMAND,
                policy.evaluate(request("smp", "list\nstop")).status());
        assertEquals(CommandBridgePolicy.Status.COMMAND_POLICY_REJECTED,
                policy.evaluate(request("smp", "list extra")).status());
    }

    private static CommandBridgeRequest request(String server, String command) {
        return new CommandBridgeRequest(
                REQUEST_ID,
                new ModerationSubjectId(SUBJECT_ID),
                DISCORD_ID,
                ACTOR_ID,
                server,
                command,
                NOW
        );
    }
}
